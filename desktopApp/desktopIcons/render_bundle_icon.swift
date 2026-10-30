// Writes the icon macOS renders for an app bundle as an iconset, used by generate.py.
// Usage: swift render_bundle_icon.swift <bundle.app> <output.iconset>

import AppKit

let arguments = CommandLine.arguments
let icon = NSWorkspace.shared.icon(forFile: arguments[1])
let iconset = URL(fileURLWithPath: arguments[2])
try FileManager.default.createDirectory(at: iconset, withIntermediateDirectories: true)

for points in [16, 32, 128, 256, 512] {
    for scale in [1, 2] {
        let pixels = points * scale
        // Takes the system's rendering at this size as is; drawing it into a bitmap context
        // instead shifts the colors slightly
        guard let rep = icon.representations.first(where: { $0.pixelsWide == pixels }),
              let image = rep.cgImage(forProposedRect: nil, context: nil, hints: nil),
              image.width == pixels
        else {
            fatalError("No \(pixels) px rendering of \(arguments[1])")
        }
        let name = scale == 1 ? "icon_\(points)x\(points).png" : "icon_\(points)x\(points)@2x.png"
        let png = NSBitmapImageRep(cgImage: image).representation(using: .png, properties: [:])!
        try png.write(to: iconset.appendingPathComponent(name))
    }
}
