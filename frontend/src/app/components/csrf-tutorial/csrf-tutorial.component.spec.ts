import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { CsrfTutorialComponent } from './csrf-tutorial.component';
import { RouterTestingModule } from '@angular/router/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ModuleListItem } from 'src/app/model/module-list-item';

describe('CsrfTutorialComponent', () => {
  let component: CsrfTutorialComponent;
  let fixture: ComponentFixture<CsrfTutorialComponent>;

  beforeEach(waitForAsync(() => {
    TestBed.configureTestingModule({
      declarations: [CsrfTutorialComponent],
      imports: [
        RouterTestingModule,
        HttpClientTestingModule,
        FormsModule,
        ReactiveFormsModule,
      ],
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(CsrfTutorialComponent);
    component = fixture.componentInstance;
    const module: ModuleListItem = {
      name: 'test',
      parameters: [],
      isSolved: false,
      locator: 'csrf',
      tags: [],
    };
    component.module = module;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
