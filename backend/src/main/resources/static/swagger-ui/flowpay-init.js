window.addEventListener('load', function () {
  SwaggerUIBundle({
    url: '/api/openapi.yaml',
    dom_id: '#swagger-ui',
    supportedSubmitMethods: [],
    validatorUrl: null,
    persistAuthorization: false
  });
});
