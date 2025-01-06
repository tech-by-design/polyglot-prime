from fastapi import FastAPI
from fastapi import FastAPI
from validate_service_nyher_fhir_ig_equivalent import validation_router



def start_application():
	app = FastAPI()
	app.include_router(validation_router)
	return app
	
app = start_application()


@app.get("/")
def Start_api():
    return {"API":"Api Initializing"}
