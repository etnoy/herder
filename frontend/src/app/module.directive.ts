import { Directive, ViewContainerRef } from '@angular/core';

@Directive({
  selector: '[appModuleUrl]',
})
export class ModuleDirective {
  constructor(public viewContainerRef: ViewContainerRef) {}
}
