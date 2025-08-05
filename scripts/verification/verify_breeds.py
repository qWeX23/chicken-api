import pandas as pd
import requests
import json
import os
import re
import warnings
from urllib3.exceptions import NotOpenSSLWarning
warnings.filterwarnings("ignore", category=NotOpenSSLWarning)

# --- Configuration ---
# You can change this to your Ollama API endpoint.
# It
#s good practice to make this configurable, e.g., via environment variables.
OLLAMA_API_URL = os.getenv("OLLAMA_API_URL", "http://localhost:11434")
OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", "llama3") # You can change this to your preferred model

# --- Functions ---

def call_ollama(prompt, model=OLLAMA_MODEL):
    """
    Calls the Ollama API with the given prompt and model.
    """
    url = f"{OLLAMA_API_URL}/api/generate"
    headers = {"Content-Type": "application/json"}
    data = {
        "model": model,
        "prompt": prompt,
        "stream": False,
        # "format": "json" # Request JSON format from Ollama
    }
    try:
        response = requests.post(url, headers=headers, data=json.dumps(data))
        response.raise_for_status()  # Raise an exception for HTTP errors
        full_ollama_response = response.json()
        print(f"Full Ollama API Response: {full_ollama_response}") # Added for debugging
        return full_ollama_response.get("response", full_ollama_response)
    except requests.exceptions.RequestException as e:
        print(f"Error calling Ollama API: {e}")
        if 'response' in locals() and response is not None:
            print(f"Ollama API Response Status Code: {response.status_code}")
            print(f"Ollama API Response Text: {response.text}")
        return None

def generate_ollama_prompt(breed_data):
    """
    Generates the prompt for the Ollama LLM based on the breed data.
    """
    system_message = """You are an expert in poultry science and an editor for a chicken breed directory. Your task is to review user-submitted chicken breeds for factual accuracy, consistency, and appropriateness. Analyze the provided breed information and determine if it is a legitimate, real-world chicken breed, and if all provided details are consistent and suitable for publication."""

    user_message = f"""
Please review the following chicken breed submission and provide your analysis in JSON format.

Breed Data:
```json
{json.dumps(breed_data, indent=2)}
```

Your Task:
Based on the data, provide a JSON object with the following structure. Pay close attention to factual accuracy, consistency between fields (e.g., does the origin make sense for the breed name, is the description consistent with other attributes?), and overall appropriateness for a general audience.

```json
{{
  "is_real_breed": boolean,
  "confidence_score": float (from 0.0 to 1.0),
  "reasoning": "Provide a brief explanation for your decision regarding factual accuracy and consistency.",
  "is_appropriate": boolean,
  "appropriateness_reasoning": "If inappropriate (e.g., offensive, non-chicken related, or nonsensical), explain why.",
  "corrected_info": {{
    "name": "Suggest a corrected name if applicable, otherwise null.",
    "origin": "Suggest a corrected origin if applicable, otherwise null.",
    "eggColor": "Suggest a corrected eggColor if applicable, otherwise null.",
    "eggSize": "Suggest a corrected eggSize if applicable, otherwise null.",
    "eggNumber": "Suggest a corrected eggNumber if applicable, otherwise null.",
    "temperment": "Suggest a corrected temperment if applicable, otherwise null.",
    "description": "Suggest a corrected description if applicable, otherwise null.",
    "imageUrl": "Suggest a corrected imageUrl if applicable, otherwise null."
  }}
}}
"""
    # Ollama expects a single prompt string, so we combine system and user messages
    return f"{system_message}\n\n{user_message}"

def verify_breeds(input_csv_path, output_csv_path, failed_output_csv_path):
    """
    Reads breed data from input_csv_path, verifies each breed using Ollama,
    and writes the results to output_csv_path.
    """
    try:
        df = pd.read_csv(input_csv_path)
    except FileNotFoundError:
        print(f"Error: Input CSV file not found at {input_csv_path}")
        return

    results = []
    for index, row in df.iterrows():
        breed_data = row.to_dict()
        print(f"Verifying breed: {breed_data.get('name', 'Unknown')}")

        prompt = generate_ollama_prompt(breed_data)
        ollama_response_str = call_ollama(prompt)

        if ollama_response_str:
            print(f"Raw Ollama response for {breed_data.get('name', 'Unknown')}:\n{ollama_response_str}\n")
            try:
                # Extract JSON string from markdown code block
                match = re.search(r'```json\n(.*?)```', ollama_response_str, re.DOTALL)
                if match:
                    json_string = match.group(1)
                    print(f"Extracted JSON string: {json_string}")
                    ollama_result = json.loads(json_string)
                    # Merge original breed data with Ollama's verification result
                    # Extract and clean fields
                    ollama_reasoning = ollama_result.get("reasoning", "").replace("\n", " ")
                    ollama_appropriateness_reasoning = ollama_result.get("appropriateness_reasoning", "").replace("\n", " ")

                    # Handle corrected_info fields, ensuring they are flattened and cleaned
                    corrected_info = ollama_result.get("corrected_info", {})
                    corrected_name = corrected_info.get("name", breed_data.get("name", ""))
                    corrected_origin = corrected_info.get("origin", breed_data.get("origin", ""))
                    corrected_eggColor = corrected_info.get("eggColor", breed_data.get("eggColor", ""))
                    corrected_eggSize = corrected_info.get("eggSize", breed_data.get("eggSize", ""))
                    corrected_eggNumber = corrected_info.get("eggNumber", breed_data.get("eggNumber", None))
                    corrected_temperment = corrected_info.get("temperment", breed_data.get("temperment", ""))
                    corrected_description = corrected_info.get("description", breed_data.get("description", ""))
                    corrected_imageUrl = corrected_info.get("imageUrl", breed_data.get("imageUrl", ""))

                    # Clean newlines from corrected_info string fields
                    corrected_name = corrected_name.replace("\n", " ") if isinstance(corrected_name, str) else corrected_name
                    corrected_origin = corrected_origin.replace("\n", " ") if isinstance(corrected_origin, str) else corrected_origin
                    corrected_eggColor = corrected_eggColor.replace("\n", " ") if isinstance(corrected_eggColor, str) else corrected_eggColor
                    corrected_eggSize = corrected_eggSize.replace("\n", " ") if isinstance(corrected_eggSize, str) else corrected_eggSize
                    corrected_temperment = corrected_temperment.replace("\n", " ") if isinstance(corrected_temperment, str) else corrected_temperment
                    corrected_description = corrected_description.replace("\n", " ") if isinstance(corrected_description, str) else corrected_description
                    corrected_imageUrl = corrected_imageUrl.replace("\n", " ") if isinstance(corrected_imageUrl, str) else corrected_imageUrl

                    short_reasoning = ""
                    if ollama_reasoning:
                        # Take the first sentence, remove trailing period if it exists, then add one
                        first_sentence = ollama_reasoning.split(". ")[0].strip()
                        if first_sentence.endswith("."):
                            short_reasoning += first_sentence
                        else:
                            short_reasoning += first_sentence + "."

                    if ollama_appropriateness_reasoning and ollama_appropriateness_reasoning != ollama_reasoning:
                        # Take the first sentence, remove trailing period if it exists, then add one
                        second_sentence = ollama_appropriateness_reasoning.split(". ")[0].strip()
                        if second_sentence.endswith("."):
                            if short_reasoning: short_reasoning += " "
                            short_reasoning += second_sentence
                        else:
                            if short_reasoning: short_reasoning += " "
                            short_reasoning += second_sentence + "."

                    # Construct the combined_result with only the desired, flattened, and cleaned fields
                    combined_result = {
                        "name": corrected_name,
                        "origin": corrected_origin,
                        "eggColor": corrected_eggColor,
                        "eggSize": corrected_eggSize,
                        "eggNumber": corrected_eggNumber,
                        "temperment": corrected_temperment,
                        "description": corrected_description,
                        "imageUrl": corrected_imageUrl,
                        "is_real_breed": ollama_result.get("is_real_breed", False),
                        "confidence_score": ollama_result.get("confidence_score", 0.0),
                        "reasoning": ollama_reasoning,
                        "is_appropriate": ollama_result.get("is_appropriate", False),
                        "appropriateness_reasoning": ollama_appropriateness_reasoning,
                        "short_reasoning": short_reasoning,
                    }
                    results.append(combined_result)
                else:
                    print(f"Could not find JSON in markdown format: {ollama_response_str}")
                    results.append({**breed_data, "ollama_error": "JSON markdown not found", "raw_ollama_response": ollama_response_str})
            except json.JSONDecodeError:
                print(f"Could not decode JSON from Ollama response: {ollama_response_str}")
                # Append original data and a note about parsing error, and the raw response
                results.append({**breed_data, "ollama_error": "JSON parsing failed", "raw_ollama_response": ollama_response_str})
        else:
            # Append original data and a note about API error
            results.append({**breed_data, "ollama_error": "API call failed", "raw_ollama_response": "No response from Ollama API"})

    if results:
        verified_breeds = []
        failed_breeds = []
        for res in results:
            if res.get("is_real_breed", False) and res.get("is_appropriate", False):
                verified_breeds.append(res)
            else:
                failed_breeds.append(res)

        if verified_breeds:
            verified_df = pd.DataFrame(verified_breeds)
            verified_df.to_csv(output_csv_path, index=False)
            print(f"Verification complete. Verified breeds saved to {output_csv_path}")
        else:
            print("No real and appropriate breeds found to save to verified_breeds.csv.")

        if failed_breeds:
            failed_df = pd.DataFrame(failed_breeds)
            failed_df.to_csv(failed_output_csv_path, index=False)
            print(f"Failed breeds saved to {failed_output_csv_path} for further review.")
        else:
            print("No failed breeds found.")
    else:
        print("No results to save.")

if __name__ == "__main__":
    # Example usage:
    # Make sure you have an 'input_breeds.csv' in the same directory
    # or provide a full path to your CSV file.
    # Example input_breeds.csv content:
    # name,origin,eggColor,eggSize,eggNumber,temperment,description,imageUrl
    # Rhode Island Red,USA,Brown,Large,250,Docile,"A dual-purpose breed, known for its egg laying ability.",http://example.com/rhode_island_red.jpg
    # Blue Andalusian,Spain,White,Medium,180,Active,"A Mediterranean breed, known for its striking blue plumage.",http://example.com/blue_andalusian.jpg

    input_file = "input_breeds.csv" # This should be the CSV you download from Google Sheets
    output_file = "verified_breeds.csv"
    failed_output_file = "failed_breeds.csv"

    # For demonstration, let's create a dummy input_breeds.csv if it doesn't exist
    if not os.path.exists(input_file):
        print(f"Creating a dummy '{input_file}' for demonstration purposes.")
        dummy_data = {
            "name": ["Rhode Island Red", "Blue Andalusian", "Imaginary Chicken"],
            "origin": ["USA", "Spain", "Mars"],
            "eggColor": ["Brown", "White", "Green"],
            "eggSize": ["Large", "Medium", "Small"],
            "eggNumber": [250, 180, 10],
            "temperment": ["Docile", "Active", "Aggressive"],
            "description": [
                "A dual-purpose breed, known for its egg laying ability.",
                "A Mediterranean breed, known for its striking blue plumage.",
                "A chicken from outer space, lays glowing green eggs."
            ],
            "imageUrl": [
                "http://example.com/rhode_island_red.jpg",
                "http://example.com/blue_andalusian.jpg",
                "http://example.com/imaginary_chicken.jpg"
            ]
        }
        dummy_df = pd.DataFrame(dummy_data)
        dummy_df.to_csv(input_file, index=False)
        print(f"Dummy '{input_file}' created. Please replace it with your actual data.")

    verify_breeds(input_file, output_file, failed_output_file)
