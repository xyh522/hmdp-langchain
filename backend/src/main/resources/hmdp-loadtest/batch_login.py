"""Create test users and export tokens obtained through the actual login API."""
import argparse
import csv
import json
import os
import sys
from pathlib import Path
from datetime import datetime
from urllib.parse import urlsplit
from concurrent.futures import ThreadPoolExecutor, as_completed

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / 'lib'))
import pymysql
import redis
import requests
import yaml


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--project', default=r'D:\java_heima_learning\hm-dianping')
    parser.add_argument('--base-url', default='http://127.0.0.1:8081')
    parser.add_argument('--db-user', default='xu')
    parser.add_argument('--count', type=int, default=500)
    parser.add_argument('--workers', type=int, default=5)
    parser.add_argument('--seed-only', action='store_true')
    parser.add_argument('--check-only', action='store_true')
    args = parser.parse_args()
    if not 1 <= args.count <= 10000 or not 1 <= args.workers <= 20:
        parser.error('count must be 1..10000, workers must be 1..20')
    cfg = yaml.safe_load((Path(args.project) / 'src/main/resources/application.yaml').read_text(encoding='utf-8'))['spring']
    ds = cfg['datasource']
    addr = urlsplit(ds['url'].removeprefix('jdbc:'))
    db = pymysql.connect(host=addr.hostname, port=addr.port or 3306,
        user=args.db_user, password=os.environ.get('HMDP_DB_PASSWORD', str(ds['password'])),
        database=addr.path.lstrip('/'), charset='utf8mb4', connect_timeout=5,
        read_timeout=15, write_timeout=15, cursorclass=pymysql.cursors.DictCursor)
    print(f'MySQL connected: {addr.hostname}:{addr.port or 3306}/{addr.path.lstrip("/")}', flush=True)
    if args.check_only:
        db.close()
        return 0
    phones = [str(13990000000 + i) for i in range(args.count)]
    marks = ','.join(['%s'] * len(phones))
    try:
        with db.cursor() as cur:
            cur.execute(f'SELECT id,phone,nick_name FROM tb_user WHERE phone IN ({marks})', phones)
            existing = {row['phone']: row for row in cur.fetchall()}
            for phone, row in existing.items():
                if row['nick_name'] != 'loadtest_' + phone:
                    raise RuntimeError(f'Phone collision with existing non-test user: {phone}')
            new = [(p, 'loadtest_' + p) for p in phones if p not in existing]
            if new:
                cur.executemany('INSERT INTO tb_user (phone,nick_name) VALUES (%s,%s)', new)
            cur.execute(f'SELECT id,phone,nick_name FROM tb_user WHERE phone IN ({marks}) ORDER BY phone', phones)
            users = cur.fetchall()
            if len(users) != args.count:
                raise RuntimeError('User count verification failed')
        db.commit()
    except Exception:
        db.rollback()
        raise
    finally:
        db.close()
    output = ROOT / 'output'
    output.mkdir(exist_ok=True)
    with (output / 'users.csv').open('w', encoding='utf-8', newline='') as file:
        writer = csv.DictWriter(file, fieldnames=['id', 'phone', 'nick_name'])
        writer.writeheader()
        writer.writerows(users)
    print(f'Users verified: {len(users)}; inserted: {len(new)}; reused: {len(existing)}', flush=True)
    if args.seed_only:
        return 0
    rc = cfg['redis']
    client = redis.Redis(host=rc['host'], port=rc.get('port', 6379), db=rc.get('database', 0),
        password=str(rc['password']) if rc.get('password') is not None else None,
        decode_responses=True, socket_connect_timeout=5, socket_timeout=5)
    client.ping()
    base = args.base_url.rstrip('/')

    def login(user):
        with requests.Session() as session:
            session.trust_env = False
            def call(method, path, **kwargs):
                response = session.request(method, base + path, timeout=(5, 15), **kwargs)
                response.raise_for_status()
                body = response.json()
                if body.get('success') is not True:
                    raise RuntimeError(body.get('errorMsg', 'API failure'))
                return body.get('data')
            call('POST', '/user/code', params={'phone': user['phone']})
            code = client.get('login:code:' + user['phone'])
            if not code:
                raise RuntimeError('Redis verification code missing')
            token = call('POST', '/user/login', json={'phone': user['phone'], 'code': code})
            if not isinstance(token, str) or not token:
                raise RuntimeError('Missing token')
            me = call('GET', '/user/me', headers={'authorization': token})
            if not me or str(me.get('id')) != str(user['id']):
                raise RuntimeError('Token identity verification failed')
            return {'id': user['id'], 'phone': user['phone'], 'token': token}

    # Verify API availability before submitting 500 login jobs.
    first = login(users[0])
    results, errors = [first], []
    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = {pool.submit(login, user): user for user in users[1:]}
        for future in as_completed(futures):
            try:
                results.append(future.result())
            except Exception as exc:
                errors.append({'phone': futures[future]['phone'], 'error': str(exc)})
            if (len(results) + len(errors)) % 50 == 0:
                print(f'Completed {len(results) + len(errors)}/{len(users)}; failed {len(errors)}', flush=True)
    client.close()
    results.sort(key=lambda row: row['phone'])
    run = output / datetime.now().strftime('%Y%m%d_%H%M%S_%f')
    run.mkdir()
    (run / 'tokens.txt').write_text(''.join(r['token'] + '\n' for r in results), encoding='utf-8')
    (run / 'tokens.json').write_text(json.dumps(results, indent=2), encoding='utf-8')
    (run / 'errors.json').write_text(json.dumps(errors, ensure_ascii=False, indent=2), encoding='utf-8')
    with (run / 'tokens.csv').open('w', encoding='utf-8', newline='') as file:
        writer = csv.DictWriter(file, fieldnames=['id', 'phone', 'token'])
        writer.writeheader()
        writer.writerows(results)
    print(f'Tokens verified: {len(results)}; errors: {len(errors)}; output: {run}')
    return 1 if errors else 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except Exception as exc:
        print(f'ERROR: {exc}', file=sys.stderr)
        sys.exit(1)
