import { UrlSegment } from '@angular/router';
import { NameValueTag } from './name-value-tag';
export class ModuleListItem {
  locator: string;
  name: string;
  tags: NameValueTag[];
  parameters: UrlSegment[];
  isSolved: boolean;
}
