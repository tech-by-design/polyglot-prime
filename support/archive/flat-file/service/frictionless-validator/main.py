from fastapi import FastAPI
from validate_service_nyher_fhir_ig_equivalent import validation_router
from fastapi.middleware.cors import CORSMiddleware

def start_application():
	app = FastAPI(
    title="CSV-Frictionless-Validator", 
    version="1.0.0"
)
	app.include_router(validation_router)
	return app
	
app = start_application()

origins = [
    #"http://localhost:8080",  # Frontend local development URL 
	"https://synthetic.csv-frictionless-validator.techbd.org",
    # Add other origins as needed, e.g., production domains
] 

app.add_middleware(
    CORSMiddleware,
    allow_origins=origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
