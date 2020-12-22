import { async, ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientModule } from '@angular/common/http';
import { CsrfTutorialComponent } from './csrf-tutorial.component';
import { RouterTestingModule } from '@angular/router/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { Module } from 'src/app/model/module';

describe('CsrfTutorialComponent', () => {
  let component: CsrfTutorialComponent;
  let fixture: ComponentFixture<CsrfTutorialComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [CsrfTutorialComponent],
      imports: [
        RouterTestingModule,
        HttpClientModule,
        FormsModule,
        ReactiveFormsModule,
      ],
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(CsrfTutorialComponent);
    component = fixture.componentInstance;
    const module: Module = {
      name: 'test',
      parameters: [],
      isSolved: null,
    };
    component.module = module;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
