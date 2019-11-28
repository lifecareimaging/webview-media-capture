var globalCordova = require('webpack/cordova');
var cordovaModule = require('webpack/cordova/module');

var createMediaCaptureAdapter = require('./createMediaCaptureAdapter.js');

// pass in global cordova object to expose cordova.exec
var MediaCaptureAdapter = createMediaCaptureAdapter(globalCordova);
cordovaModule.exports = MediaCaptureAdapter;
