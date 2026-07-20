import os

import psycopg2

mydb = psycopg2.connect(
    host=os.getenv("POSTGRES_HOST", "localhost"),
    port=os.getenv("POSTGRES_PORT", "5432"),
    dbname=os.getenv("POSTGRES_DB", "textile_management"),
    user=os.getenv("POSTGRES_USER", "textile"),
    password=os.getenv("POSTGRES_PASSWORD", "textile_password"),
)

print("Connection Established")
cur = mydb.cursor()