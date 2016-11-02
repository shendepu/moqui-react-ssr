/**
 * js-timeout-polyfill
 * @see https://blogs.oracle.com/nashorn/entry/setinterval_and_settimeout_javascript_functions
 */
(function (global) {
  'use strict';

  if (global.setTimeout ||
    global.clearTimeout ||
    global.setInterval ||
    global.clearInterval) {
    return;
  }

  var Timer = global.Java.type('java.util.Timer');

  function toCompatibleNumber(val) {
    switch (typeof val) {
      case 'number':
        break;
      case 'string':
        val = parseInt(val, 10);
        break;
      case 'boolean':
      case 'object':
        val = 0;
        break;

    }
    return val > 0 ? val : 0;
  }

  function setTimerRequest(handler, delay, interval, args) {
    handler = handler || function () {
      };
    delay = toCompatibleNumber(delay);
    interval = toCompatibleNumber(interval);

    var applyHandler = function () {
      handler.apply(this, args);
    };

    /*var runLater = function () {
     Platform.runLater(applyHandler);
     };*/

    var timer;
    if (interval > 0) {
      timer = new Timer('setIntervalRequest', true);
      timer.schedule(applyHandler, delay, interval);
    } else {
      timer = new Timer('setTimeoutRequest', false);
      timer.schedule(applyHandler, delay);
    }

    return timer;
  }

  function clearTimerRequest(timer) {
    timer.cancel();
  }

  /////////////////
  // Set polyfills
  /////////////////
  global.setInterval = function setInterval() {
    var args = Array.prototype.slice.call(arguments);
    var handler = args.shift();
    var ms = args.shift();

    return setTimerRequest(handler, ms, ms, args);
  };

  global.clearInterval = function clearInterval(timer) {
    clearTimerRequest(timer);
  };

  global.setTimeout = function setTimeout() {
    var args = Array.prototype.slice.call(arguments);
    var handler = args.shift();
    var ms = args.shift();

    return setTimerRequest(handler, ms, 0, args);
  };

  global.clearTimeout = function clearTimeout(timer) {
    clearTimerRequest(timer);
  };

  global.setImmediate = function setImmediate() {
    var args = Array.prototype.slice.call(arguments);
    var handler = args.shift();

    return setTimerRequest(handler, 0, 0, args);
  };

  global.clearImmediate = function clearImmediate(timer) {
    clearTimerRequest(timer);
  };

})(this);