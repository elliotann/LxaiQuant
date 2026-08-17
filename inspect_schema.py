import mysql.connector
import json

config = {
    "host": "192.168.1.23",
    "user": "root",
    "password": "Hxa104906",
    "database": "vdr_shop_test"
}

try:
    conn = mysql.connector.connect(**config)
    cursor = conn.cursor()
    
    cursor.execute("SHOW TABLES")
    tables = cursor.fetchall()
    
    print("Tables:")
    for table in tables:
        print(f"- {table[0]}")
        
        # Describe table to see columns
        cursor.execute(f"DESCRIBE {table[0]}")
        columns = cursor.fetchall()
        for col in columns:
            print(f"  - {col[0]} ({col[1]})")
            
    conn.close()
except Exception as e:
    print(f"Error: {e}")
