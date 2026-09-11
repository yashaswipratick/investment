import csv
import os
import sys
from datetime import date, datetime

from cassandra.cluster import Cluster
from cassandra.auth import PlainTextAuthProvider


# ============================================================
# CONFIGURATION
# ============================================================

CASSANDRA_HOST = "localhost"
CASSANDRA_PORT = 9042

KEYSPACE = "realtime_stock_data"
TABLE = "stock_history"

# If authentication is disabled, keep both as None.
# If authentication is enabled, set:
# USERNAME = "cassandra"
# PASSWORD = "cassandra"
USERNAME = None
PASSWORD = None


# ============================================================
# HELPERS
# ============================================================

def convert_value(value):
    """Convert Cassandra values into CSV-friendly values."""
    if value is None:
        return ""

    # Cassandra UDTs are commonly returned as namedtuples.
    if hasattr(value, "_asdict"):
        return {
            field_name: convert_value(field_value)
            for field_name, field_value in value._asdict().items()
        }

    # Fallback for objects exposing fields through __dict__.
    if hasattr(value, "__dict__"):
        return {
            field_name: convert_value(field_value)
            for field_name, field_value in vars(value).items()
        }

    if isinstance(value, (date, datetime)):
        return value.isoformat()

    if isinstance(value, dict):
        return {
            str(k): convert_value(v)
            for k, v in value.items()
        }

    if isinstance(value, (list, tuple, set)):
        return [convert_value(v) for v in value]

    return value


def flatten_udt(udt_value):
    """Flatten a Cassandra UDT into a dictionary."""
    if udt_value is None:
        return {}

    if hasattr(udt_value, "_asdict"):
        return {
            field_name: convert_value(field_value)
            for field_name, field_value in udt_value._asdict().items()
        }

    if hasattr(udt_value, "__dict__"):
        return {
            field_name: convert_value(field_value)
            for field_name, field_value in vars(udt_value).items()
        }

    return {"value": convert_value(udt_value)}


# ============================================================
# EXPORT
# ============================================================

def export_stock_history(primary_key, output_directory):
    cluster = None
    session = None

    try:
        print("Connecting to Cassandra...")

        if USERNAME and PASSWORD:
            auth_provider = PlainTextAuthProvider(
                username=USERNAME,
                password=PASSWORD
            )

            cluster = Cluster(
                [CASSANDRA_HOST],
                port=CASSANDRA_PORT,
                auth_provider=auth_provider
            )
        else:
            cluster = Cluster(
                [CASSANDRA_HOST],
                port=CASSANDRA_PORT
            )

        session = cluster.connect(KEYSPACE)

        print(
            f"Connected to Cassandra: "
            f"{CASSANDRA_HOST}:{CASSANDRA_PORT}"
        )

        query = f"""
            SELECT key, stock_history_details
            FROM {TABLE}
            WHERE key = %s
        """

        print(f"Fetching data for primary key: {primary_key}")

        row = session.execute(query, (primary_key,)).one()

        if row is None:
            print(f"No data found for primary key: {primary_key}")
            return None

        stock_history = row.stock_history_details

        if not stock_history:
            print(f"No stock history found for: {primary_key}")
            return None

        print(f"Found {len(stock_history)} historical records")

        csv_rows = []

        for history_date, history_details in stock_history.items():
            flattened = flatten_udt(history_details)

            csv_row = {
                "key": primary_key,
                "date": (
                    history_date.isoformat()
                    if isinstance(history_date, (date, datetime))
                    else str(history_date)
                )
            }

            csv_row.update(flattened)
            csv_rows.append(csv_row)

        os.makedirs(output_directory, exist_ok=True)

        output_file = os.path.join(
            output_directory,
            f"{primary_key}.csv"
        )

        # Build columns dynamically from the UDT fields.
        fieldnames = []

        for row_data in csv_rows:
            for field in row_data.keys():
                if field not in fieldnames:
                    fieldnames.append(field)

        # Keep key and date as the first two columns.
        fieldnames = (
            ["key", "date"] +
            [
                field
                for field in fieldnames
                if field not in ["key", "date"]
            ]
        )

        with open(
            output_file,
            "w",
            newline="",
            encoding="utf-8"
        ) as csv_file:
            writer = csv.DictWriter(
                csv_file,
                fieldnames=fieldnames,
                extrasaction="ignore"
            )

            writer.writeheader()

            for row_data in csv_rows:
                cleaned_row = {}

                for field in fieldnames:
                    value = row_data.get(field, "")

                    if isinstance(value, (dict, list, tuple, set)):
                        value = str(value)

                    cleaned_row[field] = value

                writer.writerow(cleaned_row)

        print()
        print("============================================")
        print("Export completed successfully")
        print("============================================")
        print(f"Primary Key : {primary_key}")
        print(f"Records     : {len(csv_rows)}")
        print(f"CSV File    : {output_file}")
        print("============================================")

        return output_file

    except Exception as exc:
        print()
        print("ERROR while exporting Cassandra data")
        print("--------------------------------------------")
        print(str(exc))
        print("--------------------------------------------")
        raise

    finally:
        if session:
            session.shutdown()

        if cluster:
            cluster.shutdown()


# ============================================================
# MAIN
# ============================================================

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print(
            "Usage:\n"
            "  python3 export_cassandra_to_csv.py "
            "<PRIMARY_KEY> <OUTPUT_DIRECTORY>\n\n"
            "Example:\n"
            "  python3 export_cassandra_to_csv.py "
            "MUNJALAU ~/Downloads/cassandra_export"
        )
        sys.exit(1)

    primary_key = sys.argv[1]
    output_directory = os.path.expanduser(sys.argv[2])

    export_stock_history(
        primary_key,
        output_directory
    )
