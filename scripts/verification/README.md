# Chicken Breed Verification Pipeline

This document provides instructions on how to set up and run the Python script for verifying chicken breed data using Ollama.

## Setup and Running Instructions

### 1. Navigate to the Project Root:
Open your terminal or command prompt and navigate to the root directory of your `chicken-api` project:

```bash
cd /Users/benjaminchurchill/Github/chicken-api
```

### 2. Create a Python Virtual Environment:
It's best practice to use a virtual environment to manage Python dependencies. This keeps your project's dependencies separate from your system's Python installation.

```bash
python3 -m venv venv_chicken_api
```
This command creates a new directory named `venv_chicken_api` (you can name it anything you like) in your project root, which will contain the virtual environment.

### 3. Activate the Virtual Environment:

*   **On macOS/Linux:**
    ```bash
    source venv_chicken_api/bin/activate
    ```
*   **On Windows (Command Prompt):**
    ```bash
    venv_chicken_api\Scripts\activate.bat
    ```
*   **On Windows (PowerShell):**
    ```bash
    venv_chicken_api\Scripts\Activate.ps1
    ```
You'll know the virtual environment is active because your terminal prompt will change to include the name of your virtual environment (e.g., `(venv_chicken_api) your_username@your_computer:~/Github/chicken-api$`).

### 4. Install Dependencies:
With the virtual environment activated, install the required Python libraries using the `requirements.txt` file:

```bash
pip install -r scripts/verification/requirements.txt
```

### 5. Prepare Your Input CSV File:
The script expects an input CSV file named `input_breeds.csv` in the `scripts/verification/` directory. This file should contain the chicken breed data you downloaded from your Google Sheet, with the following headers: `name,origin,eggColor,eggSize,eggNumber,temperment,description,imageUrl`.

*   **Important:** Place your actual CSV file at `chicken-api/scripts/verification/input_breeds.csv`.
*   For demonstration purposes, if `input_breeds.csv` doesn't exist, the script will create a dummy one for you to see how it works. **Remember to replace this dummy file with your real data.**

### 6. Configure Ollama API URL (Optional but Recommended):
By default, the script assumes your Ollama API is running at `http://localhost:11434`. If your Ollama instance is on a different machine or port, you can set the `OLLAMA_API_URL` environment variable before running the script:

```bash
# Example for macOS/Linux
export OLLAMA_API_URL="http://your_ollama_ip:port"

# Example for Windows (Command Prompt)
set OLLAMA_API_URL="http://your_ollama_ip:port"

# Example for Windows (PowerShell)
$env:OLLAMA_API_URL="http://your_ollama_ip:port"
```
You can also change the `OLLAMA_MODEL` environment variable if you want to use a different model than `llama3`.

### 7. Run the Verification Script:
Now, execute the Python script:

```bash
python scripts/verification/verify_breeds.py
```

### 8. Review the Output:
The script will process each breed and save the results to a new CSV file named `verified_breeds.csv` in the `scripts/verification/` directory. This file will include all your original data plus the LLM's verification results (e.g., `is_real_breed`, `reasoning`, `corrected_info`).

### 9. Deactivate the Virtual Environment (When Done):
When you're finished working with the script, you can deactivate the virtual environment:

```bash
deactivate
```
This returns your terminal to its normal state.
