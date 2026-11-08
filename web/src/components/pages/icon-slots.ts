// Fills <span data-icon="name"></span> placeholders in an HTML string (Markdown content) with inline SVG.
import { icon, type IconName } from '../../lib/icons';

export const fillIconSlots = (html: string): string =>
  html.replace(/<span([^>]*?)\sdata-icon="([a-zA-Z0-9]+)"([^>]*)><\/span>/g, (_m, a: string, name: string, b: string) =>
    `<span${a} data-icon="${name}"${b}>${icon(name as IconName)}</span>`);
