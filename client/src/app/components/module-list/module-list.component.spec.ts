import { TestBed, waitForAsync } from '@angular/core/testing';

import { ModuleListComponent } from './module-list.component';
import { ApiService } from 'src/app/service/api.service';
import { Module } from 'src/app/model/module';
import { of } from 'rxjs';
import { MockInstance, MockProvider, MockRender } from 'ng-mocks';

describe('ModuleListComponent', () => {
  beforeEach(
    waitForAsync(() => {
      TestBed.configureTestingModule({
        declarations: [ModuleListComponent],
        providers: [MockProvider(ApiService)],
      }).compileComponents();
    })
  );

  beforeEach(() => {});

  it('should create', () => {
    const mockModule = new Module();

    mockModule.name = 'Test module';
    mockModule.locator = 'test-module';

    const getModules = MockInstance(
      ApiService,
      'getModules',
      jasmine.createSpy()
    ).and.returnValue(of([mockModule]));

    const fixture = MockRender(ModuleListComponent);

    expect(fixture.point.componentInstance.modules).toEqual([mockModule]);
    expect(getModules).toHaveBeenCalled();
  });
});
