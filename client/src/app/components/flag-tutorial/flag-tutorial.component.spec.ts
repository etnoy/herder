import { async, ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientModule } from '@angular/common/http';
import { FlagTutorialComponent } from './flag-tutorial.component';
import { RouterTestingModule } from '@angular/router/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { Module } from 'src/app/model/module';

describe('FlagTutorialComponent', () => {
  let component: FlagTutorialComponent;
  let fixture: ComponentFixture<FlagTutorialComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [FlagTutorialComponent],
      imports: [
        RouterTestingModule,
        HttpClientModule,
        FormsModule,
        ReactiveFormsModule,
      ],
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(FlagTutorialComponent);
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
