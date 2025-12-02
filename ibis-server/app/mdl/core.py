from functools import cache

import analytics_core


@cache
def get_session_context(
    manifest_str: str | None, function_path: str, properties: frozenset | None = None
) -> analytics_core.SessionContext:
    return analytics_core.SessionContext(manifest_str, function_path, properties)


def get_manifest_extractor(manifest_str: str) -> analytics_core.ManifestExtractor:
    return analytics_core.ManifestExtractor(manifest_str)


def to_json_base64(manifest) -> str:
    return analytics_core.to_json_base64(manifest)
