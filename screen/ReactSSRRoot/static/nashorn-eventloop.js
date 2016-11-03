(function nashornEventLoopMain(context) {
  'use strict';

  var Timer = Java.type('java.util.Timer');
  var Phaser = Java.type('java.util.concurrent.Phaser');
  var System = Java.type('java.lang.System');
  var TimeUnit = Java.type('java.util.concurrent.TimeUnit');
  var RequestBuilder = Java.type('org.apache.http.client.methods.RequestBuilder');
  var FutureCallback = Java.type('org.apache.http.concurrent.FutureCallback');
  var HttpAsyncClientBuilder = Java.type('org.apache.http.impl.nio.client.HttpAsyncClientBuilder');

  var finalException = null;

  function newTimer() {
    timer = new Timer('jsEventLoop', false);
    if (typeof applicationProperties !== "undefined") {
      applicationProperties.put("timer", timer);
    }
  }

  function newPhaser() {
    phaser = new Phaser();
    if (typeof applicationProperties !== "undefined") {
      applicationProperties.put("phaser", phaser);
    }
  }

  var timer;
  var phaser;

  newTimer();
  newPhaser();

  var onTaskFinished = function () {
    phaser.arriveAndDeregister();
  };

  context.shutdown = function () {
    timer.cancel();
    phaser.forceTermination();
  };

  context.setTimeout = function (fn, millis /* [, args...] */) {
    var args = [].slice.call(arguments, 2, arguments.length);

    var phase = phaser.register();
    var canceled = false;
    timer.schedule(function () {
      if (canceled) {
        return;
      }

      try {
        fn.apply(context, args);
        phaser.arriveAndDeregister();
      } catch (e) {

        // Store the error
        finalException = e;

        // Clear the phaser blocks and the timer
        // This drops main to end
        context.shutdown();
      }
    }, millis);

    return function () {
      phaser.arriveAndDeregister();
      canceled = true;
    };
  };

  context.clearTimeout = function (cancel) {
    cancel();
  };

  context.setInterval = function (fn, delay /* [, args...] */) {
    var args = [].slice.call(arguments, 2, arguments.length);

    var cancel = null;

    var loop = function () {
      cancel = context.setTimeout(loop, delay);
      fn.apply(context, args);
    };

    cancel = context.setTimeout(loop, delay);
    return function () {
      cancel();
    };
  };

  context.clearInterval = function (cancel) {
    cancel();
  };

  /**
   * This function actually just waits. While it is running, a second thread can
   * embed a timer containing a timeout call which arrives and deregisters, handles
   * exceptions, and so on. This allows JavaScript functions in one thread to handle
   * and respond to changes from a second.
   */
  context.eventLoop = function () {
    if (phaser.isTerminated()) {
      newPhaser();
    }

    var original = phaser.register();
    phaser.awaitAdvance(original);

    phaser.arriveAndDeregister();

    if (finalException) {
      throw finalException;
    }
  };

  /**
   * A main wait function. This gets called with a function that can start timeouts
   * and so on, but which doesn't truly exit until everything is completed.
   */
  context.main = function (fn, waitTimeMillis) {

    if (!waitTimeMillis) {
      waitTimeMillis = 60 * 1000;
    }

    if (phaser.isTerminated()) {
      newPhaser();
    }

    // we register the main(...) function with the phaser so that we
    // can be notified of all cases. If we wouldn't do this, we would have a
    // race condition as `fn` could be finished before we call `await(...)`
    // on the phaser.
    phaser.register();
    setTimeout(fn, 0);

    // timeout is handled via TimeoutException. This is good enough for us.
    phaser.awaitAdvanceInterruptibly(phaser.arrive(),
      waitTimeMillis,
      TimeUnit.MILLISECONDS);

    // a new phase will have started, so we need to arrive and deregister
    // to make sure that following executions of main(...) will work as well.
    phaser.arriveAndDeregister();

    if (finalException) {
      throw finalException;
    }
  };

  context.XMLHttpRequest = function () {
    var method, url, async, user, password, headers = {};

    var that = this;

    this.onreadystatechange = function () {
    };

    this.onload = function () {
    };

    this.readyState = 0;
    this.response = null;
    this.responseText = null;
    this.responseType = '';
    this.status = null;
    this.statusText = null;
    this.timeout = 0; // no timeout by default
    this.ontimeout = function () {
    };
    this.withCredentials = false;
    this.requestBuilder = null;

    this.abort = function () {

    };

    this.getAllResponseHeaders = function () {

    };

    this.getResponseHeader = function (key) {

    };

    this.setRequestHeader = function (key, value) {
      headers[key] = value;
    };

    this.open = function (_method, _url, _async, _user, _password) {
      this.readyState = 1;

      method = _method;
      url = _url;

      async = _async === false ? false : true;

      user = _user || '';
      password = _password || '';

      this.requestBuilder = RequestBuilder.create(_method);
      this.requestBuilder.setUri(_url);

      setTimeout(this.onreadystatechange, 0);
    };

    this.send = function (data) {
      phaser.register();
      var that = this;

      var clientBuilder = HttpAsyncClientBuilder.create();
      var httpclient = clientBuilder.build();
      httpclient.start();

      var callback = new FutureCallback({
        completed: function (response) {
          that.readyState = 4;

          var body = org.apache.http.util.EntityUtils.toString(response.getEntity(), 'UTF-8');
          that.responseText = that.response = body;

          if (that.responseType === 'json') {
            try {
              that.response = JSON.parse(that.response);
            } catch (e) {

              // Store the error
              finalException = e;

              context.shutdown();
            }
          }

          if (finalException) {
            return;
          }

          var statusLine = response.getStatusLine();
          that.status = statusLine.getStatusCode();
          that.statusText = statusLine.getReasonPhrase();

          context.setTimeout(that.onreadystatechange, 0);
          context.setTimeout(that.onload, 0);

          phaser.arriveAndDeregister();
        },
        cancelled: function () {
          System.err.println("Cancelled");
        },
        failed: function (e) {

          that.readyState = 4;
          that.status = 0;
          that.statusText = e.getMessage();
          context.setTimeout(that.onreadystatechange, 0);
          context.setTimeout(that.onerror, 0);

          phaser.arriveAndDeregister();
        }
      });

      httpclient.execute(this.requestBuilder.build(), null, callback);
    }
  }

})(this);