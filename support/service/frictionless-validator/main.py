from fastapi import FastAPI
from validate_service_nyher_fhir_ig_equivalent import validation_router

def start_application():
	app = FastAPI(
    title="CSV-Frictionless-Validator", 
    version="1.0.0"
)
	app.include_router(validation_router)
	return app
	
app = start_application()