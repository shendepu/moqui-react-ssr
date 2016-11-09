function renderServer () {
  return app['render']();
};

function getState () {
   return app['getState']();
};

function newApp() {
  return new app.default();
};
