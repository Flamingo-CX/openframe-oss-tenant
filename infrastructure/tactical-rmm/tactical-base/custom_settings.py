from corsheaders.defaults import default_headers

CORS_ALLOW_ALL_ORIGINS = False
CORS_ALLOW_CREDENTIALS = True
CORS_ALLOWED_ORIGINS = [
    "http://localhost:8080"
]
CORS_ALLOW_METHODS = [
    "DELETE",
    "GET",
    "OPTIONS",
    "PATCH",
    "POST",
    "PUT",
]
CORS_ALLOW_HEADERS = list(default_headers) + [
    "accept",
    "accept-encoding",
    "authorization",
    "content-type",
    "dnt",
    "origin",
    "user-agent",
    "x-csrftoken",
    "x-requested-with",
]

# Session and CSRF settings
SESSION_COOKIE_DOMAIN = None
CSRF_COOKIE_DOMAIN = None
CSRF_TRUSTED_ORIGINS = [
    "http://localhost:8080"
]
CSRF_COOKIE_SAMESITE = 'Lax'
SESSION_COOKIE_SAMESITE = 'Lax'
CSRF_COOKIE_SECURE = False
SESSION_COOKIE_SECURE = False

# Ensure CORS whitelist matches allowed origins
CORS_ORIGIN_WHITELIST = CORS_ALLOWED_ORIGINS

# Allauth settings
ACCOUNT_EMAIL_VERIFICATION = 'none'
ACCOUNT_AUTHENTICATION_METHOD = 'username'
ACCOUNT_EMAIL_REQUIRED = False

# Additional CORS settings
CORS_URLS_REGEX = r'^.*$'  # Allow CORS for all URLs
CORS_EXPOSE_HEADERS = ['content-type', 'x-csrftoken']

# Disable 2FA
TRMM_DISABLE_2FA = True