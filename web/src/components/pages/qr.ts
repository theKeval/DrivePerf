// Real, scannable QR codes as inline SVG (qrcode package). Used at build time by QrCode.astro, and lazily in the
// browser by the Ride-along page when a pass code must be encoded.
import QRCode from 'qrcode';

export const QR_LABEL = 'QR code that opens this Ride-along pass on your phone';

export async function qrSvg(text: string): Promise<string> {
  const svg = await QRCode.toString(text, { type: 'svg', margin: 0, errorCorrectionLevel: 'M', color: { dark: '#0B0E13', light: '#ffffff' } });
  return svg.replace('<svg ', `<svg role="img" aria-label="${QR_LABEL}" `);
}
