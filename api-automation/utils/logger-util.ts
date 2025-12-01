import winston from "winston";
import fs from "fs";
import path from "path";

class Logger {
  private logger: winston.Logger;

  constructor() {
    const logPath = path.join(__dirname, "logs.log");

    this.logger = winston.createLogger({
      level: "info",
      format: winston.format.json(),
      transports: [
        new winston.transports.Console(),
        //new winston.transports.File({ filename: logPath }),
      ],
    });
  }

  public info(message: string): void {
    this.logger.info(message);
  }

  public error(message: string, error?: any): void {
    this.logger.error(message);
  }

  public warn(message: string): void {
    this.logger.warn(message);
  }

  public debug(message: string): void {
    this.logger.debug(message);
  }
}

export default Logger;
