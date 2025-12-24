import Foundation
import ScreenCaptureKit
import CoreMedia
import CoreVideo
import CoreGraphics
import ImageIO
import UniformTypeIdentifiers

// New simplified callback - just notify frame saved
public typealias SCKFrameCb = @convention(c) (Int32, UnsafeMutableRawPointer?) -> Void

@available(macOS 12.3, *)
final class SCKOutput: NSObject, SCStreamOutput {
    let cb: SCKFrameCb
    let user: UnsafeMutableRawPointer?
    let deliveryQueue: DispatchQueue
    let outputDir: URL
    let jpegQuality: CGFloat
    let scale: CGFloat

    init(cb: @escaping SCKFrameCb, user: UnsafeMutableRawPointer?, deliveryQueue: DispatchQueue, outputDir: String, jpegQuality: Int32, scale: Float) {
        self.cb = cb
        self.user = user
        self.deliveryQueue = deliveryQueue
        self.outputDir = URL(fileURLWithPath: outputDir)
        self.jpegQuality = CGFloat(jpegQuality) / 100.0
        self.scale = CGFloat(scale)
    }
    func stream(_ stream: SCStream, didOutputSampleBuffer sbuf: CMSampleBuffer, of type: SCStreamOutputType) {
        guard type == .screen, let pix = CMSampleBufferGetImageBuffer(sbuf) else { return }

        // Update frame counter
        gFrameCount &+= 1
        let idx = gFrameCount

        // Log every 10 frames
        if idx % 10 == 0 {
            fputs("[SCKBridge] Processing frame \(idx)\n", stderr)
        }

        // Process on background queue to not block SCK
        deliveryQueue.async {
            // 1. Create CGImage from pixel buffer
            guard let image = self.createCGImage(from: pix) else {
                fputs("[SCKBridge] Failed to create CGImage\n", stderr)
                return
            }

            // 2. Scale if needed
            let scaled = self.scale != 1.0 ? self.scaleImage(image, scale: self.scale) : image

            // 3. Encode to JPEG
            guard let jpegData = self.encodeToJPEG(scaled, quality: self.jpegQuality) else {
                fputs("[SCKBridge] Failed to encode JPEG\n", stderr)
                return
            }

            // 4. Write to disk
            let filename = String(format: "ffcap_%06d.jpg", idx - 1)
            let fileURL = self.outputDir.appendingPathComponent(filename)
            do {
                try jpegData.write(to: fileURL)
                // 5. Notify Kotlin
                self.cb(Int32(idx - 1), self.user)
            } catch {
                fputs("[SCKBridge] Failed to write JPEG: \(error)\n", stderr)
            }
        }
    }

    private func createCGImage(from pixelBuffer: CVPixelBuffer) -> CGImage? {
        CVPixelBufferLockBaseAddress(pixelBuffer, .readOnly)
        defer { CVPixelBufferUnlockBaseAddress(pixelBuffer, .readOnly) }

        let width = CVPixelBufferGetWidth(pixelBuffer)
        let height = CVPixelBufferGetHeight(pixelBuffer)
        let bytesPerRow = CVPixelBufferGetBytesPerRow(pixelBuffer)
        guard let baseAddress = CVPixelBufferGetBaseAddress(pixelBuffer) else { return nil }

        let colorSpace = CGColorSpaceCreateDeviceRGB()
        let bitmapInfo = CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue)

        guard let context = CGContext(
            data: baseAddress,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: bytesPerRow,
            space: colorSpace,
            bitmapInfo: bitmapInfo.rawValue
        ) else { return nil }

        return context.makeImage()
    }

    private func scaleImage(_ image: CGImage, scale: CGFloat) -> CGImage {
        let newWidth = Int(CGFloat(image.width) * scale)
        let newHeight = Int(CGFloat(image.height) * scale)

        let colorSpace = CGColorSpaceCreateDeviceRGB()
        let bitmapInfo = CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue)

        guard let context = CGContext(
            data: nil,
            width: newWidth,
            height: newHeight,
            bitsPerComponent: 8,
            bytesPerRow: newWidth * 4,
            space: colorSpace,
            bitmapInfo: bitmapInfo.rawValue
        ) else { return image }

        context.interpolationQuality = .high
        context.draw(image, in: CGRect(x: 0, y: 0, width: newWidth, height: newHeight))

        return context.makeImage() ?? image
    }

    private func encodeToJPEG(_ image: CGImage, quality: CGFloat) -> Data? {
        let data = NSMutableData()
        guard let destination = CGImageDestinationCreateWithData(data, UTType.jpeg.identifier as CFString, 1, nil) else {
            return nil
        }

        let options: [CFString: Any] = [
            kCGImageDestinationLossyCompressionQuality: quality
        ]

        CGImageDestinationAddImage(destination, image, options as CFDictionary)
        guard CGImageDestinationFinalize(destination) else { return nil }

        return data as Data
    }
}

@available(macOS 12.3, *)
private var gStream: SCStream?
@available(macOS 12.3, *)
private var gOutput: SCKOutput?
@available(macOS 12.3, *)
private var gFilter: SCContentFilter?
@available(macOS 12.3, *)
private var gQueue: DispatchQueue?
@available(macOS 12.3, *)
private var gDelegate: SCKDelegate?
@available(macOS 12.3, *)
private var gFrameCount: Int64 = 0
@available(macOS 12.3, *)
private var gCbQueue: DispatchQueue?

@available(macOS 12.3, *)
final class SCKDelegate: NSObject, SCStreamDelegate {
    func stream(_ stream: SCStream, didStopWithError error: Error) {
        fputs("[SCKBridge] Stream stopped with error: \(error.localizedDescription)\n", stderr)
    }
}

@available(macOS 12.3, *)
final class SendableBox<T>: @unchecked Sendable { var value: T?; init(_ v: T? = nil) { self.value = v } }

@available(macOS 12.3, *)
private func fetchShareableContentBlocking() -> (SCShareableContent?, String?) {
    let sema = DispatchSemaphore(value: 0)
    let contentBox = SendableBox<SCShareableContent>()
    let errBox = SendableBox<String>()
    Task.detached {
        do { contentBox.value = try await SCShareableContent.current }
        catch { errBox.value = error.localizedDescription }
        sema.signal()
    }
    let timeout = DispatchTime.now() + .seconds(3)
    if sema.wait(timeout: timeout) == .timedOut {
        return (nil, "Timed out awaiting SCShareableContent.current")
    }
    return (contentBox.value, errBox.value)
}

@_cdecl("sck_list_displays_json")
public func sck_list_displays_json() -> UnsafeMutablePointer<CChar>? {
    guard #available(macOS 12.3, *) else { return nil }
    let (contentOpt, err) = fetchShareableContentBlocking()
    if let err = err { fputs("[SCKBridge] sck_list_displays_json content error: \(err)\n", stderr) }
    guard let content = contentOpt else { return nil }
    do {
        let arr = content.displays.map { d in
            ["id": d.displayID, "width": Int(d.width), "height": Int(d.height)] as [String: Any]
        }
        let data = try JSONSerialization.data(withJSONObject: arr, options: [])
        let out = UnsafeMutablePointer<CChar>.allocate(capacity: data.count + 1)
        _ = data.withUnsafeBytes { buf in
            memcpy(out, buf.baseAddress!, data.count)
        }
        out[data.count] = 0
        return out
    } catch {
        fputs("[SCKBridge] sck_list_displays_json JSON error: \(error.localizedDescription)\n", stderr)
        return nil
    }
}

@_cdecl("sck_start_display_capture")
public func sck_start_display_capture(
    _ displayId: UInt32,
    _ fps: Int32,
    _ x: Int32,
    _ y: Int32,
    _ w: Int32,
    _ h: Int32,
    _ outputDir: UnsafePointer<CChar>?,
    _ jpegQuality: Int32,
    _ scale: Float,
    _ cb: @escaping SCKFrameCb,
    _ user: UnsafeMutableRawPointer?
) -> Int32 {
    guard #available(macOS 12.3, *) else { return -100 }

    guard let outputDirPtr = outputDir else { return -5 }
    let outputDirString = String(cString: outputDirPtr)

    let (contentOpt, err) = fetchShareableContentBlocking()
    if let err = err { fputs("[SCKBridge] sck_start_display_capture content error: \(err)\n", stderr) }
    guard let content = contentOpt else { return -3 }
    if content.displays.isEmpty { return -4 }
    guard let target = content.displays.first(where: { $0.displayID == displayId }) else { return -2 }
    let filter = SCContentFilter(display: target, excludingWindows: [])
    let cfg = SCStreamConfiguration()
    cfg.pixelFormat = kCVPixelFormatType_32BGRA
    cfg.minimumFrameInterval = CMTime(value: 1, timescale: fps > 0 ? fps : 60)
    // Optional region capture
    if w > 0 && h > 0 {
        cfg.sourceRect = CGRect(x: Int(x), y: Int(y), width: Int(w), height: Int(h))
        cfg.width = Int(w); cfg.height = Int(h)
    } else {
        cfg.width = target.width; cfg.height = target.height
    }
    cfg.queueDepth = 30
    let delegate = SCKDelegate()
    let stream = SCStream(filter: filter, configuration: cfg, delegate: delegate)
    let cbq = DispatchQueue(label: "gifland.sck.cb", qos: .userInitiated)
    let output = SCKOutput(cb: cb, user: user, deliveryQueue: cbq, outputDir: outputDirString, jpegQuality: jpegQuality, scale: scale)
    let q = DispatchQueue(label: "gifland.sck.output", qos: .userInitiated)
    do {
        try stream.addStreamOutput(output, type: .screen, sampleHandlerQueue: q)
    } catch {
        fputs("[SCKBridge] addStreamOutput error: \(error.localizedDescription)\n", stderr)
        return -3
    }
    stream.startCapture()
    gFilter = filter; gStream = stream; gOutput = output; gQueue = q; gDelegate = delegate; gFrameCount = 0; gCbQueue = cbq
    fputs("[SCKBridge] Started stream on displayId=\(displayId) fps=\(fps) size=\(target.width)x\(target.height)\n", stderr)
    return 0
}

@_cdecl("sck_stop_capture")
public func sck_stop_capture() {
    guard #available(macOS 12.3, *) else { return }
    if let s = gStream { s.stopCapture() }
    gStream = nil; gOutput = nil; gFilter = nil; gQueue = nil; gDelegate = nil; gFrameCount = 0
}

// MARK: - Permission Management

/// Check if screen recording permission is granted
/// Returns: 0 = granted, 1 = not granted/not determined
@_cdecl("sck_check_permission")
public func sck_check_permission() -> Int32 {
    if #available(macOS 10.15, *) {
        return CGPreflightScreenCaptureAccess() ? 0 : 1
    }
    return 0 // Older macOS doesn't require permission
}

/// Request screen recording permission (shows system dialog if not determined)
/// Returns: 0 = granted, 1 = denied
@_cdecl("sck_request_permission")
public func sck_request_permission() -> Int32 {
    if #available(macOS 10.15, *) {
        return CGRequestScreenCaptureAccess() ? 0 : 1
    }
    return 0
}

/// Trigger permission registration by attempting to access SCShareableContent
/// This registers the app in Privacy settings even if permission is not yet granted
/// Returns: 0 = success (app registered), 1 = failed
@_cdecl("sck_trigger_permission_registration")
public func sck_trigger_permission_registration() -> Int32 {
    guard #available(macOS 12.3, *) else { return 0 }

    fputs("[SCKBridge] Triggering permission registration...\n", stderr)

    // Accessing SCShareableContent.current triggers macOS to register
    // this app in the Screen Recording privacy list
    let (contentOpt, err) = fetchShareableContentBlocking()

    if let err = err {
        fputs("[SCKBridge] Permission registration triggered (with error: \(err))\n", stderr)
        // Even if it fails with permission error, the app gets registered
        return 0
    }

    if let content = contentOpt {
        fputs("[SCKBridge] Permission registration successful. Found \(content.displays.count) display(s)\n", stderr)
        return 0
    }

    fputs("[SCKBridge] Permission registration completed\n", stderr)
    return 0
}
