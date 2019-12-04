import Foundation
import AVFoundation
import Photos

@objc(MediaCapture)
class MediaCapture : CDVPlugin, AVCaptureFileOutputRecordingDelegate {
    
    var videoIndex = 1
    var finalOutputFileUrl: URL?
    
    func merge(arrayVideos:[AVAsset], completion:@escaping (_ exporter: AVAssetExportSession) -> ()) -> Void {
        let mainComposition = AVMutableComposition()
        let compositionVideoTrack = mainComposition.addMutableTrack(withMediaType: .video, preferredTrackID: kCMPersistentTrackID_Invalid)
        compositionVideoTrack?.preferredTransform = CGAffineTransform(rotationAngle: .pi / 2)
        let soundtrackTrack = mainComposition.addMutableTrack(withMediaType: .audio, preferredTrackID: kCMPersistentTrackID_Invalid)
        var time:CMTime = CMTimeMakeWithSeconds(0, preferredTimescale: 1000000)

        for (index, videoAsset) in arrayVideos.enumerated() {
            let atTime = time
            let audioTracks = videoAsset.tracks(withMediaType: .audio)
            let videoTracks = videoAsset.tracks(withMediaType: .video)
            
            if(videoTracks.count > 0) {
                try! compositionVideoTrack?.insertTimeRange(CMTimeRangeMake(start: CMTime.zero, duration: videoAsset.duration), of: videoTracks[0], at: atTime)
            }
            
            if(audioTracks.count > 0) {
                try! soundtrackTrack?.insertTimeRange(CMTimeRangeMake(start: CMTime.zero, duration: videoAsset.duration), of: audioTracks[0], at: atTime)
            }

            time = CMTimeAdd(time, videoAsset.duration)
        }
        
        let outputFileURL = URL(fileURLWithPath: NSTemporaryDirectory() + "merge.mp4")
        let fileManager = FileManager()

        do {
            try fileManager.removeItem(at: outputFileURL)
        } catch let error as NSError {
            print("Error: \(error.domain)")
        }

        let exporter = AVAssetExportSession(asset: mainComposition, presetName: AVAssetExportPresetHighestQuality)
        exporter?.outputURL = outputFileURL
        exporter?.outputFileType = AVFileType.mp4
        exporter?.shouldOptimizeForNetworkUse = true
        exporter?.exportAsynchronously {
            DispatchQueue.main.async {
                completion(exporter!)
            }
        }
    }
    
    func deleteFile(fileUrl: URL) {
        do {
            let fileManager = FileManager()
            try fileManager.removeItem(at: fileUrl)
        } catch {
            print("Could not delete file")
        }
    }
    
    func fileOutput(_ output: AVCaptureFileOutput, didFinishRecordingTo outputFileURL: URL, from connections: [AVCaptureConnection], error: Error?) {
        
    }
    
    class CameraView: UIView {
        var videoPreviewLayer:AVCaptureVideoPreviewLayer?
        
        func interfaceOrientationToVideoOrientation(_ orientation : UIInterfaceOrientation) -> AVCaptureVideoOrientation {
            switch (orientation) {
            case UIInterfaceOrientation.portrait:
                return AVCaptureVideoOrientation.portrait;
            case UIInterfaceOrientation.portraitUpsideDown:
                return AVCaptureVideoOrientation.portraitUpsideDown;
            case UIInterfaceOrientation.landscapeLeft:
                return AVCaptureVideoOrientation.landscapeLeft;
            case UIInterfaceOrientation.landscapeRight:
                return AVCaptureVideoOrientation.landscapeRight;
            default:
                return AVCaptureVideoOrientation.portraitUpsideDown;
            }
        }

        override func layoutSubviews() {
            super.layoutSubviews();
            if let sublayers = self.layer.sublayers {
                for layer in sublayers {
                    layer.frame = self.bounds;
                }
            }
            
            self.videoPreviewLayer?.connection?.videoOrientation = interfaceOrientationToVideoOrientation(UIApplication.shared.statusBarOrientation);
        }
        
        
        func addPreviewLayer(_ previewLayer:AVCaptureVideoPreviewLayer?) {
            previewLayer!.videoGravity = AVLayerVideoGravity.resizeAspectFill
            previewLayer!.frame = self.bounds
            self.layer.addSublayer(previewLayer!)
            self.videoPreviewLayer = previewLayer;
        }
        
        func removePreviewLayer() {
            if self.videoPreviewLayer != nil {
                self.videoPreviewLayer!.removeFromSuperlayer()
                self.videoPreviewLayer = nil
            }
        }
    }

    var cameraView: CameraView!
    var captureSession:AVCaptureSession?
    var commandCallbackId: String?

    var captureVideoPreviewLayer:AVCaptureVideoPreviewLayer?
    var videoFileOutput: AVCaptureMovieFileOutput?
    var outputFileUrl: NSURL?

    var currentCamera: Int = 0;
    var frontCamera: AVCaptureDevice?
    var backCamera: AVCaptureDevice?
    var microphone: AVCaptureDevice?
    var connection: AVMediaType?

    var paused: Bool = false
    var recording: Bool = false
    var muted: Bool = false

    enum MediaCaptureError: Int32 {
        case unexpected_error = 0,
        camera_access_denied = 1,
        camera_access_restricted = 2,
        back_camera_unavailable = 3,
        front_camera_unavailable = 4,
        camera_unavailable = 5,
        light_unavailable = 7,
        open_settings_unavailable = 8
    }

    enum CaptureError: Error {
        case backCameraUnavailable
        case frontCameraUnavailable
        case couldNotCaptureInput(error: NSError)
    }

    enum LightError: Error {
        case torchUnavailable
    }

    override func pluginInitialize() {
        super.pluginInitialize()
        NotificationCenter.default.addObserver(self, selector: #selector(pageDidLoad), name: NSNotification.Name.CDVPageDidLoad, object: nil)
        self.cameraView = CameraView(frame: CGRect(x: 0, y: 0, width: UIScreen.main.bounds.width, height: UIScreen.main.bounds.height))
        self.cameraView.autoresizingMask = [.flexibleWidth, .flexibleHeight];
    }

    func sendErrorCode(command: CDVInvokedUrlCommand, error: MediaCaptureError){
        let pluginResult = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: error.rawValue)
        commandDelegate!.send(pluginResult, callbackId:command.callbackId)
    }

    // utility method
    @objc func backgroundThread(delay: Double = 0.0, background: (() -> Void)? = nil, completion: (() -> Void)? = nil) {
        if #available(iOS 8.0, *) {
            DispatchQueue.global(qos: DispatchQoS.QoSClass.userInitiated).async {
                if (background != nil) {
                    background!()
                }
                DispatchQueue.main.asyncAfter(deadline: DispatchTime.now() + delay * Double(NSEC_PER_SEC)) {
                    if(completion != nil){
                        completion!()
                    }
                }
            }
        } else {
            // Fallback for iOS < 8.0
            if(background != nil){
                background!()
            }
            if(completion != nil){
                completion!()
            }
        }
    }

    @objc func prepCamera(command: CDVInvokedUrlCommand) -> Bool{
        let status = AVCaptureDevice.authorizationStatus(for: AVMediaType.video)
        if (status == AVAuthorizationStatus.restricted) {
            self.sendErrorCode(command: command, error: MediaCaptureError.camera_access_restricted)
            return false
        } else if status == AVAuthorizationStatus.denied {
            self.sendErrorCode(command: command, error: MediaCaptureError.camera_access_denied)
            return false
        }
        do {
            if (captureSession?.isRunning != true){
                cameraView.backgroundColor = UIColor.clear
                self.webView!.superview!.insertSubview(cameraView, belowSubview: self.webView!)
                let availableVideoDevices =  AVCaptureDevice.devices(for: AVMediaType.video)

                for device in availableVideoDevices {
                    if device.position == AVCaptureDevice.Position.back {
                        backCamera = device
                    }
                    else if device.position == AVCaptureDevice.Position.front {
                        frontCamera = device
                    }
                }

                let availableAudioDevices = AVCaptureDevice.devices(for: AVMediaType.audio)
                microphone = availableAudioDevices[0]

                let audioInput = try self.createCaptureAudioDeviceInput()
                videoFileOutput = AVCaptureMovieFileOutput()
                
                let connection = videoFileOutput!.connection(with: .video)
                if videoFileOutput!.availableVideoCodecTypes.contains(.h264) {
                    // Use the H.264 codec to encode the video.
                    videoFileOutput!.setOutputSettings([AVVideoCodecKey: AVVideoCodecType.h264], for: connection!)
                }

                let documentsPath = NSSearchPathForDirectoriesInDomains(.documentDirectory, .userDomainMask, true)[0] as! NSString
                let outputPath = "\(documentsPath)/lccamera\(videoIndex).mp4"

                captureSession = AVCaptureSession()
                outputFileUrl = NSURL(fileURLWithPath: outputPath)
                captureSession!.addOutput(videoFileOutput!)

                // older iPods have no back camera
                if(backCamera == nil){
                    currentCamera = 1
                }
                let videoInput: AVCaptureDeviceInput
                videoInput = try self.createCaptureDeviceInput()

                captureSession!.addInput(videoInput)
                captureSession!.addInput(audioInput)

                captureVideoPreviewLayer = AVCaptureVideoPreviewLayer(session: captureSession!)
                cameraView.addPreviewLayer(captureVideoPreviewLayer)
                captureSession!.startRunning()
            }
            return true
        } catch CaptureError.backCameraUnavailable {
            self.sendErrorCode(command: command, error: MediaCaptureError.back_camera_unavailable)
        } catch CaptureError.frontCameraUnavailable {
            self.sendErrorCode(command: command, error: MediaCaptureError.front_camera_unavailable)
        } catch CaptureError.couldNotCaptureInput(let error){
            print(error.localizedDescription)
            self.sendErrorCode(command: command, error: MediaCaptureError.camera_unavailable)
        } catch {
            self.sendErrorCode(command: command, error: MediaCaptureError.unexpected_error)
        }
        return false
    }

    @objc func createCaptureDeviceInput() throws -> AVCaptureDeviceInput {
        var captureDevice: AVCaptureDevice
        if(currentCamera == 0){
            if(backCamera != nil){
                captureDevice = backCamera!
            } else {
                throw CaptureError.backCameraUnavailable
            }
        } else {
            if(frontCamera != nil){
                captureDevice = frontCamera!
            } else {
                throw CaptureError.frontCameraUnavailable
            }
        }
        let captureDeviceInput: AVCaptureDeviceInput
        do {
            captureDeviceInput = try AVCaptureDeviceInput(device: captureDevice)
        } catch let error as NSError {
            throw CaptureError.couldNotCaptureInput(error: error)
        }
        return captureDeviceInput
    }

    @objc func createCaptureAudioDeviceInput() throws -> AVCaptureDeviceInput {
        var captureDevice: AVCaptureDevice
        captureDevice = microphone!

        let captureDeviceInput: AVCaptureDeviceInput
        do {
            captureDeviceInput = try AVCaptureDeviceInput(device: captureDevice)
        } catch let error as NSError {
            throw CaptureError.couldNotCaptureInput(error: error)
        }
        return captureDeviceInput
    }

    @objc func makeOpaque(){
        self.webView?.isOpaque = false
        self.webView?.backgroundColor = UIColor.clear
    }

    @objc func boolToNumberString(bool: Bool) -> String{
        if(bool) {
            return "1"
        } else {
            return "0"
        }
    }

    @objc func configureLight(command: CDVInvokedUrlCommand, state: Bool){
        var useMode = AVCaptureDevice.TorchMode.on
        if(state == false){
            useMode = AVCaptureDevice.TorchMode.off
        }
        do {
            // torch is only available for back camera
            if(backCamera == nil || backCamera!.hasTorch == false || backCamera!.isTorchAvailable == false || backCamera!.isTorchModeSupported(useMode) == false){
                throw LightError.torchUnavailable
            }
            try backCamera!.lockForConfiguration()
            backCamera!.torchMode = useMode
            backCamera!.unlockForConfiguration()
            self.getStatus(command)
        } catch LightError.torchUnavailable {
            self.sendErrorCode(command: command, error: MediaCaptureError.light_unavailable)
        } catch let error as NSError {
            print(error.localizedDescription)
            self.sendErrorCode(command: command, error: MediaCaptureError.unexpected_error)
        }
    }

    @objc func pageDidLoad() {
        self.webView?.isOpaque = false
        self.webView?.backgroundColor = UIColor.clear
    }

    @objc func prepare(_ command: CDVInvokedUrlCommand){
        let videoStatus = AVCaptureDevice.authorizationStatus(for: AVMediaType.video)
        if (videoStatus == AVAuthorizationStatus.notDetermined) {
            AVCaptureDevice.requestAccess(for: AVMediaType.video, completionHandler: { (granted) -> Void in
                self.backgroundThread(delay: 0, completion: {
                    if(self.prepCamera(command: command)){
                        self.getStatus(command)
                    }
                })
            })
        } else {
            if(self.prepCamera(command: command)){
                self.getStatus(command)
            }
        }

        let audioStatus = AVCaptureDevice.authorizationStatus(for: AVMediaType.audio)
        if (audioStatus == AVAuthorizationStatus.notDetermined) {
            AVCaptureDevice.requestAccess(for: AVMediaType.audio, completionHandler: { (granted) -> Void in
                self.backgroundThread(delay: 0, completion: {
                    if(self.prepCamera(command: command)){
                        self.getStatus(command)
                    }
                })
            })
        } else {
            if(self.prepCamera(command: command)){
                self.getStatus(command)
            }
        }
    }

    @objc func show(_ command: CDVInvokedUrlCommand) {
        self.webView?.isOpaque = false
        self.webView?.backgroundColor = UIColor.clear
        self.getStatus(command)
    }

    @objc func hide(_ command: CDVInvokedUrlCommand) {
        self.makeOpaque()
        self.getStatus(command)
    }

    @objc func pausePreview(_ command: CDVInvokedUrlCommand) {
        paused = true;
        captureVideoPreviewLayer?.connection?.isEnabled = false
        self.getStatus(command)
    }

    @objc func resumePreview(_ command: CDVInvokedUrlCommand) {
        paused = false;
        captureVideoPreviewLayer?.connection?.isEnabled = true
        self.getStatus(command)
    }

    @objc func muteSound(_ command: CDVInvokedUrlCommand) {
        muted = true
        captureSession!.beginConfiguration()

        let audioConnection :AVCaptureConnection? = videoFileOutput?.connection(with:AVMediaType.audio)
        
        if let connection = audioConnection {
            connection.isEnabled = false;
        }
        captureSession!.commitConfiguration()
    }

    @objc func unmuteSound(_ command: CDVInvokedUrlCommand) {
        muted = false
        captureSession!.beginConfiguration()
        let audioConnection :AVCaptureConnection? = videoFileOutput?.connection(with:AVMediaType.audio)
        
        if let connection = audioConnection {
            connection.isEnabled = true;
        }
        captureSession!.commitConfiguration()
    }

    @objc func record(_ command: CDVInvokedUrlCommand) {
        let fileManager = FileManager.default
        let documentsURL = fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
        
        do {
            let fileURLs = try fileManager.contentsOfDirectory(at: documentsURL, includingPropertiesForKeys: nil)
            for fileUrl in fileURLs {
                if (fileUrl.lastPathComponent.contains("lccamera")) {
                    deleteFile(fileUrl: fileUrl)
                }
            }
        } catch {
            print("Error while enumerating files \(documentsURL.path): \(error.localizedDescription)")
        }
        
        videoFileOutput?.startRecording(to: outputFileUrl! as URL, recordingDelegate: self)
        self.getStatus(command)
    }

    @objc func stopRecording(_ command: CDVInvokedUrlCommand) {
        videoFileOutput?.stopRecording()
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) { // Change `2.0` to the desired number of seconds.
            let fileManager = FileManager.default
            let documentsURL = fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
            do {
                var videos = [AVAsset]()
                var fileURLs = try fileManager.contentsOfDirectory(at: documentsURL, includingPropertiesForKeys: nil)
                fileURLs.sort(by: { (url1: URL, url2: URL) -> Bool in return url1.lastPathComponent < url2.lastPathComponent })

                for fileUrl in fileURLs {
                    let video = AVAsset(url: fileUrl)
                    if (fileUrl.lastPathComponent.contains("lccamera")) {
                        videos.append(video)
                    }
                }
                
                print("---")
                print(videos.count)
                print("---")
                
                self.merge(arrayVideos: videos, completion: { exporter in
                    for video in videos {
                        let urlAsset = video as! AVURLAsset
                        self.deleteFile(fileUrl: urlAsset.url)
                    }
                    
                    let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: exporter.outputURL?.absoluteString)
                    self.commandDelegate!.send(pluginResult, callbackId: command.callbackId)
                })
            } catch {
                print("Error while enumerating files \(documentsURL.path): \(error.localizedDescription)")
            }
        }
        /*
        merge(arrayVideos: arrayVideos, completion: { exporter in
            let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: exporter.outputURL?.absoluteString)
            self.commandDelegate!.send(pluginResult, callbackId: command.callbackId)
            
            self.arrayVideos = [AVAsset]()
        }) */
    }

    @objc func pauseRecording(_ command: CDVInvokedUrlCommand) {
        videoFileOutput?.stopRecording()
        self.getStatus(command)
    }

    @objc func resumeRecording(_ command: CDVInvokedUrlCommand) {
        videoIndex += 1
        
        let documentsPath = NSSearchPathForDirectoriesInDomains(.documentDirectory, .userDomainMask, true)[0] as! NSString
        let outputPath = "\(documentsPath)/lccamera\(videoIndex).mp4"
        outputFileUrl = NSURL(fileURLWithPath: outputPath)

        videoFileOutput?.startRecording(to: outputFileUrl! as URL, recordingDelegate: self)
        self.getStatus(command)
    }

    // backCamera is 0, frontCamera is 1

    @objc func useCamera(_ command: CDVInvokedUrlCommand){
        let index = command.arguments[0] as! Int
        if(currentCamera != index){
            // camera change only available if both backCamera and frontCamera exist
            if(backCamera != nil && frontCamera != nil){
                // switch camera
                currentCamera = index
                if(self.prepCamera(command: command)){
                    do {
                        captureSession!.beginConfiguration()

                        if let inputs = captureSession!.inputs as? [AVCaptureDeviceInput] {
                            for input in inputs {
                                if input.device.deviceType != AVCaptureDevice.DeviceType.builtInMicrophone {
                                    captureSession?.removeInput(input)
                                } else {
                                    print("Keep the microphone!")
                                }
                            }
                        }

                        let input = try self.createCaptureDeviceInput()
                        captureSession!.addInput(input)
                        captureSession!.commitConfiguration()

                        self.getStatus(command)
                    } catch CaptureError.backCameraUnavailable {
                        self.sendErrorCode(command: command, error: MediaCaptureError.back_camera_unavailable)
                    } catch CaptureError.frontCameraUnavailable {
                        self.sendErrorCode(command: command, error: MediaCaptureError.front_camera_unavailable)
                    } catch CaptureError.couldNotCaptureInput(let error){
                        print(error.localizedDescription)
                        self.sendErrorCode(command: command, error: MediaCaptureError.camera_unavailable)
                    } catch {
                        self.sendErrorCode(command: command, error: MediaCaptureError.unexpected_error)
                    }

                }
            } else {
                if(backCamera == nil){
                    self.sendErrorCode(command: command, error: MediaCaptureError.back_camera_unavailable)
                } else {
                    self.sendErrorCode(command: command, error: MediaCaptureError.front_camera_unavailable)
                }
            }
        } else {
            // immediately return status if camera is unchanged
            self.getStatus(command)
        }
    }

    @objc func enableLight(_ command: CDVInvokedUrlCommand) {
        if(self.prepCamera(command: command)){
            self.configureLight(command: command, state: true)
        }
    }

    @objc func disableLight(_ command: CDVInvokedUrlCommand) {
        if(self.prepCamera(command: command)){
            self.configureLight(command: command, state: false)
        }
    }

    @objc func destroy(_ command: CDVInvokedUrlCommand) {
        self.makeOpaque()
        if(self.captureSession != nil){
            backgroundThread(delay: 0, background: {
                self.captureSession!.stopRunning()
                self.cameraView.removePreviewLayer()
                self.captureVideoPreviewLayer = nil
                self.captureSession = nil
                self.currentCamera = 0
                self.frontCamera = nil
                self.backCamera = nil
                self.videoFileOutput = nil
                self.microphone = nil
            }, completion: {
                self.getStatus(command)
            })
        } else {
            self.getStatus(command)
        }
    }

    @objc func getStatus(_ command: CDVInvokedUrlCommand){

        let authorizationStatusVideo = AVCaptureDevice.authorizationStatus(for: AVMediaType.video);
        let authorizationStatusAudio = AVCaptureDevice.authorizationStatus(for: AVMediaType.audio);

        var authorized = false
        if(authorizationStatusVideo == AVAuthorizationStatus.authorized && authorizationStatusAudio == AVAuthorizationStatus.authorized){
            authorized = true
        }

        var denied = false
        if(authorizationStatusVideo == AVAuthorizationStatus.denied && authorizationStatusAudio == AVAuthorizationStatus.denied){
            denied = true
        }

        var restricted = false
        if(authorizationStatusVideo == AVAuthorizationStatus.restricted && authorizationStatusAudio == AVAuthorizationStatus.restricted){
            restricted = true
        }

        var prepared = false
        if(captureSession?.isRunning == true){
            prepared = true
        }

        var previewing = false
        if(captureVideoPreviewLayer != nil){
            previewing = captureVideoPreviewLayer!.connection!.isEnabled
        }

        var showing = false
        if(self.webView!.backgroundColor == UIColor.clear){
            showing = true
        }

        var lightEnabled = false
        if(backCamera?.torchMode == AVCaptureDevice.TorchMode.on){
            lightEnabled = true
        }

        var canOpenSettings = false
        if #available(iOS 8.0, *) {
            canOpenSettings = true
        }

        var canEnableLight = false
        if(backCamera?.hasTorch == true && backCamera?.isTorchAvailable == true && backCamera?.isTorchModeSupported(AVCaptureDevice.TorchMode.on) == true){
            canEnableLight = true
        }

        var canChangeCamera = false;
        if(backCamera != nil && frontCamera != nil){
            canChangeCamera = true
        }

        let status = [
            "authorized": boolToNumberString(bool: authorized),
            "denied": boolToNumberString(bool: denied),
            "restricted": boolToNumberString(bool: restricted),
            "prepared": boolToNumberString(bool: prepared),
            "previewing": boolToNumberString(bool: previewing),
            "showing": boolToNumberString(bool: showing),
            "muted": boolToNumberString(bool: muted),
            "recording": boolToNumberString(bool: recording),
            "paused": boolToNumberString(bool: paused),
            "lightEnabled": boolToNumberString(bool: lightEnabled),
            "canOpenSettings": boolToNumberString(bool: canOpenSettings),
            "canEnableLight": boolToNumberString(bool: canEnableLight),
            "canChangeCamera": boolToNumberString(bool: canChangeCamera),
            "currentCamera": String(currentCamera)
        ]

        let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: status)
        commandDelegate!.send(pluginResult, callbackId:command.callbackId)
    }

    @objc func openSettings(_ command: CDVInvokedUrlCommand) {
        if #available(iOS 10.0, *) {
            guard let settingsUrl = URL(string: UIApplication.openSettingsURLString) else {
            return
        }
        if UIApplication.shared.canOpenURL(settingsUrl) {
            UIApplication.shared.open(settingsUrl, completionHandler: { (success) in
                self.getStatus(command)
            })
        } else {
            self.sendErrorCode(command: command, error: MediaCaptureError.open_settings_unavailable)
            }
        } else {
            // pre iOS 10.0
            if #available(iOS 8.0, *) {
                UIApplication.shared.openURL(NSURL(string: UIApplication.openSettingsURLString)! as URL)
                self.getStatus(command)
            } else {
                self.sendErrorCode(command: command, error: MediaCaptureError.open_settings_unavailable)
            }
        }
    }
}
