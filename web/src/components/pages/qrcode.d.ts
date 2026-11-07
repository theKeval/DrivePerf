// Minimal types for the part of the `qrcode` package the site uses (avoids an @types dependency).
declare module 'qrcode' {
  interface QRCodeToStringOptions {
    type?: 'svg' | 'utf8' | 'terminal';
    margin?: number;
    errorCorrectionLevel?: 'L' | 'M' | 'Q' | 'H';
    color?: { dark?: string; light?: string };
  }
  const QRCode: { toString(text: string, options?: QRCodeToStringOptions): Promise<string> };
  export default QRCode;
}
