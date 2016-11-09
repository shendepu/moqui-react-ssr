function renderServer () {
  return app['render']();
};

function getState () {
   return app['getState']();
};

var newApp = new app.default();
