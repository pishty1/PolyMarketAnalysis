#!/usr/bin/env python3
import requests
import sys
import json
import argparse

GAMMA_API_URL = "https://gamma-api.polymarket.com"

def get_details(entity_id):
    """
    Fetches details for a given entity ID (Event or Market) from Polymarket Gamma API.
    """
    # Try fetching as an Event first
    print(f"Checking if ID {entity_id} is an Event...")
    try:
        response = requests.get(f"{GAMMA_API_URL}/events/{entity_id}")
        if response.status_code == 200:
            data = response.json()
            print(f"Found Event with ID {entity_id}")
            return data, "Event"
        elif response.status_code != 404:
            print(f"Error fetching Event: {response.status_code} - {response.text}")
    except Exception as e:
        print(f"Exception fetching Event: {e}")

    # Try fetching as a Market
    print(f"Checking if ID {entity_id} is a Market...")
    try:
        response = requests.get(f"{GAMMA_API_URL}/markets/{entity_id}")
        if response.status_code == 200:
            data = response.json()
            print(f"Found Market with ID {entity_id}")
            return data, "Market"
        elif response.status_code != 404:
            print(f"Error fetching Market: {response.status_code} - {response.text}")
    except Exception as e:
        print(f"Exception fetching Market: {e}")

    return None, None

def main():
    parser = argparse.ArgumentParser(description="Fetch Polymarket Event or Market details by ID.")
    parser.add_argument("id", help="The parentEntityId or entity ID to look up")
    args = parser.parse_args()

    entity_id = args.id
    data, entity_type = get_details(entity_id)

    if data:
        print(f"\n--- {entity_type} Details ---")
        
        slug = data.get("slug")
        if slug:
            print(f"Link: https://polymarket.com/event/{slug}")

        # print(json.dumps(data, indent=2))
        
        if entity_type == "Event":
            # If it's an event, it might have markets inside
            markets = data.get("markets", [])
            if markets:
                print(f"\n--- Associated Markets ({len(markets)}) ---")
                for market in markets:
                    print(f"- ID: {market.get('id')}")
                    print(f"  Question: {market.get('question')}")
                    print(f"  Slug: {market.get('slug')}")
    else:
        print(f"\nCould not find Event or Market with ID {entity_id}")
        sys.exit(1)

if __name__ == "__main__":
    main()
