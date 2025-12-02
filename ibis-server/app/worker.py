from uvicorn.workers import UvicornWorker


class AnalyticsUvicornWorker(UvicornWorker):
    CONFIG_KWARGS = {"loop": "uvloop", "http": "httptools"}
