import argparse
import csv
import json
import requests
from typing import Dict, Any


def query_ollama(host: str, prompt: str, model: str = "llama2") -> Dict[str, Any]:
    url = f"{host.rstrip('/')}/api/generate"
    try:
        response = requests.post(url, json={"model": model, "prompt": prompt, "stream": False})
        response.raise_for_status()
        data = response.json()
        text = data.get("response", "").strip()
        try:
            return json.loads(text)
        except json.JSONDecodeError:
            return {"analysis": text}
    except requests.RequestException as e:
        print(f"Error querying Ollama API at {url} with prompt: {prompt}")
        print(f"Exception: {e}")
        return {"analysis": "Error querying Ollama API"}


def build_prompt(row: Dict[str, str]) -> str:
    return (
        "Determine if the following entry describes a real chicken breed, is factual and mostly accurate,"
        " and does not contain harmful content. Respond only with JSON containing the fields:\n"
        "real_breed (true/false), factual (true/false), harmful (true/false), analysis (string).\n\n"
        f"Name: {row.get('name')}\n"
        f"Origin: {row.get('origin')}\n"
        f"Egg color: {row.get('eggColor')}\n"
        f"Egg size: {row.get('eggSize')}\n"
        f"Egg number: {row.get('eggNumber')}\n"
        f"Temperament: {row.get('temperament')}\n"
        f"Description: {row.get('description')}\n"
    )


def main():
    parser = argparse.ArgumentParser(description="Validate chicken breed review queue with Ollama")
    parser.add_argument("--ollama-url", required=True, help="Base URL of the Ollama host, e.g. http://localhost:11434")
    parser.add_argument("--input-csv", required=True, help="Path to the review queue CSV")
    parser.add_argument("--output-csv", required=True, help="Path to write the annotated CSV")
    parser.add_argument("--model", default="llama2", help="Model name to use for validation")
    args = parser.parse_args()

    with open(args.input_csv, newline="") as f_in, open(args.output_csv, "w", newline="") as f_out:
        reader = csv.DictReader(f_in)
        fieldnames = reader.fieldnames or []
        extra_fields = ["real_breed", "factual", "harmful", "analysis"]
        writer = csv.DictWriter(f_out, fieldnames=fieldnames + extra_fields)
        writer.writeheader()

        for row in reader:
            prompt = build_prompt(row)
            result = query_ollama(args.ollama_url, prompt, model=args.model)
            row.update({
                "real_breed": result.get("real_breed"),
                "factual": result.get("factual"),
                "harmful": result.get("harmful"),
                "analysis": result.get("analysis"),
            })
            writer.writerow(row)


if __name__ == "__main__":
    main()

