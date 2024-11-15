import json
import sys

def process_input(interaction_id: str) -> str:
    # Create the result dictionary
    result = {
        "interaction_id": interaction_id,
        "valid": True
    }
    
    # Return the result as a JSON string
    return json.dumps(result)

# Example usage
if __name__ == "__main__":
    # Read the interaction_id from command-line arguments
    if len(sys.argv) > 1:
        interaction_id = sys.argv[1]
    else:
        interaction_id = "default_id"  # Fallback if no argument is provided
    
    # Print the result to standard output
    print(process_input(interaction_id))
