var global = this;
var self = this;
var window = this;
var process = {env: {}};
var console = {};
console.debug = print;
console.warn = print;
console.log = consoleLogInfo;
console.error = consoleLogError;
console.trace = print;

// https://developer.mozilla.org/en/docs/Web/JavaScript/Reference/Global_Objects/Object/assign#Polyfill
if (!Object.assign) {
  Object.defineProperty(Object, 'assign', {
    enumerable: false,
    configurable: true,
    writable: true,
    value: function (target) {
      'use strict';
      if (target === undefined || target === null) {
        throw new TypeError('Cannot convert first argument to object');
      }

      var to = Object(target);
      for (var i = 1; i < arguments.length; i++) {
        var nextSource = arguments[i];
        if (nextSource === undefined || nextSource === null) {
          continue;
        }
        nextSource = Object(nextSource);

        var keysArray = Object.keys(nextSource);
        for (var nextIndex = 0, len = keysArray.length; nextIndex < len; nextIndex++) {
          var nextKey = keysArray[nextIndex];
          var desc = Object.getOwnPropertyDescriptor(nextSource, nextKey);
          if (desc !== undefined && desc.enumerable) {
            to[nextKey] = nextSource[nextKey];
          }
        }
      }
      return to;
    }
  });
};

// (function(self) {
//   var support = {
//     searchParams: 'URLSearchParams' in self,
//     iterable: 'Symbol' in self && 'iterator' in Symbol,
//     blob: 'FileReader' in self && 'Blob' in self && (function () {
//       try {
//         new Blob()
//         return true
//       } catch (e) {
//         return false
//       }
//     })(),
//     formData: 'FormData' in self,
//     arrayBuffer: 'ArrayBuffer' in self
//   }
//   console.log(support);
//   console.log(self.ArrayBuffer.isView);
//   console.log(self.require);
//   console.log(Array.prototype);
//   console.log(Array.prototype.map);
// })(this);
