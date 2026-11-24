# API Error Contract

FlowPay API errors use `application/problem+json` and the RFC 9457 problem-details fields. Every response has the same shape:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Revise os campos informados.",
  "instance": "/api/financial-titles",
  "requestId": "9a6c8955-737d-4296-bbf6-bdebe545706c",
  "errors": [
    { "field": "amountMinor", "code": "Positive", "message": "must be greater than 0" }
  ]
}
```

- `type`, `title`, `status`, `detail`, and `instance` follow RFC 9457. `instance` is the request path and never contains its query string.
- `requestId` matches the server-generated `X-Request-Id` response header. Clients can include it when reporting a failure.
- `errors` is always an array. Validation entries contain a field, validation code, and framework validation message. Rejected values are never returned. Messages may be localized; clients should branch on HTTP status and validation `field`/`code`, not message text.
- Application status errors retain their HTTP status and safe domain detail. Malformed requests and framework errors use stable generic details for their status.
- Authentication failures return 401; authorization and CSRF failures return 403. Both use generic details and never echo security exception messages.
- Unexpected controller failures return 500 with a generic detail. The server logs the request ID and exception type, without exception message or request data.

The complete resource-by-resource OpenAPI document, including request and success-response schemas, remains a separate contract increment.
