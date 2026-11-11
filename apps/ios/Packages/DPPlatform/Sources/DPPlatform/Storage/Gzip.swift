import Foundation
import zlib

/// Gzip (RFC 1952) with the system zlib, for `TripFileStore.compress`.
enum Gzip {
    enum Failure: Error { case zlib(Int32) }

    /// zlib's window bits for a gzip wrapper instead of a zlib one.
    private static let gzipWindowBits: Int32 = 15 + 16
    private static let chunk = 64 * 1024

    static func compress(_ input: Data) throws -> Data {
        var stream = z_stream()
        let status = deflateInit2_(
            &stream, Z_DEFAULT_COMPRESSION, Z_DEFLATED, gzipWindowBits, 8, Z_DEFAULT_STRATEGY, ZLIB_VERSION,
            Int32(MemoryLayout<z_stream>.size))
        guard status == Z_OK else { throw Failure.zlib(status) }
        defer { deflateEnd(&stream) }
        return try run(&stream, input: input, finish: Z_FINISH) { deflate(&$0, $1) }
    }

    static func decompress(_ input: Data) throws -> Data {
        var stream = z_stream()
        let status = inflateInit2_(&stream, gzipWindowBits, ZLIB_VERSION, Int32(MemoryLayout<z_stream>.size))
        guard status == Z_OK else { throw Failure.zlib(status) }
        defer { inflateEnd(&stream) }
        return try run(&stream, input: input, finish: Z_NO_FLUSH) { inflate(&$0, $1) }
    }

    private static func run(
        _ stream: inout z_stream, input: Data, finish: Int32, step: (inout z_stream, Int32) -> Int32
    ) throws -> Data {
        var output = Data()
        var buffer = [UInt8](repeating: 0, count: chunk)
        var input = input
        try input.withUnsafeMutableBytes { (raw: UnsafeMutableRawBufferPointer) in
            stream.next_in = raw.bindMemory(to: Bytef.self).baseAddress
            stream.avail_in = uInt(raw.count)
            var status: Int32 = Z_OK
            repeat {
                let produced = try buffer.withUnsafeMutableBufferPointer { out -> Int in
                    stream.next_out = out.baseAddress
                    stream.avail_out = uInt(out.count)
                    status = step(&stream, finish)
                    guard status == Z_OK || status == Z_STREAM_END || status == Z_BUF_ERROR else {
                        throw Failure.zlib(status)
                    }
                    return out.count - Int(stream.avail_out)
                }
                output.append(buffer, count: produced)
                if status == Z_BUF_ERROR && produced == 0 { throw Failure.zlib(status) }
            } while status != Z_STREAM_END
        }
        return output
    }
}
