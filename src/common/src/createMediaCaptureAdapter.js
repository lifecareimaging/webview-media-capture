module.exports = function createMediaCapture(cordova) {

  function stringToBool(string) {
    switch (string) {
      case '1':
        return true;
      case '0':
        return false;
      default:
        throw new Error('Plugin returned an invalid boolean number-string: ' + string);
    }
  }

  function convertStatus(statusDictionary) {
    return {
      authorized: stringToBool(statusDictionary.authorized),
      denied: stringToBool(statusDictionary.denied),
      restricted: stringToBool(statusDictionary.restricted),
      prepared: stringToBool(statusDictionary.prepared),
      previewing: stringToBool(statusDictionary.previewing),
      showing: stringToBool(statusDictionary.showing),
      muted: stringToBool(statusDictionary.muted),
      recording: stringToBool(statusDictionary.recording),
      paused: stringToBool(statusDictionary.paused),
      currentCamera: parseInt(statusDictionary.currentCamera)
    };
  }

  function clearBackground() {
    var body = document.body;
    if (body.style) {
      body.style.backgroundColor = 'rgba(0,0,0,0.01)';
      body.style.backgroundImage = '';
      setTimeout(function () {
        body.style.backgroundColor = 'transparent';
      }, 1);
      if (body.parentNode && body.parentNode.style) {
        body.parentNode.style.backgroundColor = 'transparent';
        body.parentNode.style.backgroundImage = '';
      }
    }
  }

  function errorCallback(callback) {
    if (!callback) {
      return null;
    }
    return function (error) {
      var errorCode = parseInt(error);
      var MediaCaptureError = {};
      switch (errorCode) {
        case 0:
          MediaCaptureError = {
            name: 'UNEXPECTED_ERROR',
            code: 0,
            _message: 'MediaCapture experienced an unexpected error.'
          };
          break;
        case 1:
          MediaCaptureError = {
            name: 'CAMERA_ACCESS_DENIED',
            code: 1,
            _message: 'The user denied camera access.'
          };
          break;
        case 2:
          MediaCaptureError = {
            name: 'CAMERA_ACCESS_RESTRICTED',
            code: 2,
            _message: 'Camera access is restricted.'
          };
          break;
        case 3:
          MediaCaptureError = {
            name: 'BACK_CAMERA_UNAVAILABLE',
            code: 3,
            _message: 'The back camera is unavailable.'
          };
          break;
        case 4:
          MediaCaptureError = {
            name: 'FRONT_CAMERA_UNAVAILABLE',
            code: 4,
            _message: 'The front camera is unavailable.'
          };
          break;
        case 5:
          MediaCaptureError = {
            name: 'CAMERA_UNAVAILABLE',
            code: 5,
            _message: 'The camera is unavailable.'
          };
          break;
        case 6:
          MediaCaptureError = {
            name: 'LIGHT_UNAVAILABLE',
            code: 7,
            _message: 'The device light is unavailable.'
          };
          break;
        case 7:
          MediaCaptureError = {
            name: 'OPEN_SETTINGS_UNAVAILABLE',
            code: 8,
            _message: 'The device is unable to open settings.'
          };
          break;
        default:
          MediaCaptureError = {
            name: 'UNEXPECTED_ERROR',
            code: 0,
            _message: 'MediaCapture returned an invalid error code.'
          };
          break;
      }
      callback(MediaCaptureError);
    };
  }

  function successCallback(callback) {
    if (!callback) {
      return null;
    }
    return function (statusDict) {
      callback(null, convertStatus(statusDict));
    };
  }

  function doneCallback(callback, clear) {
    if (!callback) {
      return null;
    }
    return function (statusDict) {
      if (clear) {
        clearBackground();
      }
      callback(convertStatus(statusDict));
    };
  }

  return {
    prepare: function (callback) {
      cordova.exec(successCallback(callback), errorCallback(callback), 'MediaCapture', 'prepare', []);
    },
    destroy: function (callback) {
      cordova.exec(doneCallback(callback, true), null, 'MediaCapture', 'destroy', []);
    },
    show: function (callback) {
      cordova.exec(doneCallback(callback, true), null, 'MediaCapture', 'show', []);
    },
    hide: function (callback) {
      cordova.exec(doneCallback(callback, true), null, 'MediaCapture', 'hide', []);
    },
    pausePreview: function (callback) {
      cordova.exec(doneCallback(callback), null, 'MediaCapture', 'pausePreview', []);
    },
    resumePreview: function (callback) {
      cordova.exec(doneCallback(callback), null, 'MediaCapture', 'resumePreview', []);
    },
    enableLight: function (callback) {
      cordova.exec(successCallback(callback), errorCallback(callback), 'MediaCapture', 'enableLight', []);
    },
    disableLight: function (callback) {
      cordova.exec(successCallback(callback), errorCallback(callback), 'MediaCapture', 'disableLight', []);
    },
    useCamera: function (index, callback) {
      cordova.exec(successCallback(callback), errorCallback(callback), 'MediaCapture', 'useCamera', [index]);
    },
    unmuteSound: function (callback) {
      cordova.exec(successCallback(callback), errorCallback(callback), 'MediaCapture', 'unmuteSound', []);
    },
    muteSound: function (callback) {
      cordova.exec(successCallback(callback), errorCallback(callback), 'MediaCapture', 'muteSound', []);
    },
    record: function (callback) {
      cordova.exec(successCallback(callback), errorCallback(callback), 'MediaCapture', 'record', []);
    },
    stopRecording: function (callback) {
      cordova.exec(callback, errorCallback(callback), 'MediaCapture', 'stopRecording', []);
    },
    pauseRecording: function (callback) {
      cordova.exec(successCallback(callback), errorCallback(callback), 'MediaCapture', 'pauseRecording', []);
    },
    resumeRecording: function (callback) {
      cordova.exec(successCallback(callback), errorCallback(callback), 'MediaCapture', 'resumeRecording', []);
    },
    useFrontCamera: function (callback) {
      var frontCamera = 1;
      if (callback) {
        this.useCamera(frontCamera, callback);
      } else {
        cordova.exec(null, null, 'MediaCapture', 'useCamera', [frontCamera]);
      }
    },
    useBackCamera: function (callback) {
      var backCamera = 0;
      if (callback) {
        this.useCamera(backCamera, callback);
      } else {
        cordova.exec(null, null, 'MediaCapture', 'useCamera', [backCamera]);
      }
    },
    openSettings: function (callback) {
      if (callback) {
        cordova.exec(successCallback(callback), errorCallback(callback), 'MediaCapture', 'openSettings', []);
      } else {
        cordova.exec(null, null, 'MediaCapture', 'openSettings', []);
      }
    },
    getStatus: function (callback) {
      if (!callback) {
        throw new Error('No callback provided to getStatus method.');
      }
      cordova.exec(doneCallback(callback), null, 'MediaCapture', 'getStatus', []);
    },
    nativeCamera: function (callback) {
      cordova.exec(successCallback(callback), errorCallback(callback), 'MediaCapture', 'nativeCamera', []);
    },
  };
};
