import { UrlSegment } from '@angular/router';
export class Module {
  name: string;
  parameters: UrlSegment[];
  isSolved: boolean;
}
