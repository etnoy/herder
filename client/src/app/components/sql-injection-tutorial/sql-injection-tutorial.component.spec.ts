import { async, ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientModule } from '@angular/common/http';
import { SqlInjectionTutorialComponent } from './sql-injection-tutorial.component';
import { RouterTestingModule } from '@angular/router/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

describe('SqlInjectionTutorialComponent', () => {
  let component: SqlInjectionTutorialComponent;
  let fixture: ComponentFixture<SqlInjectionTutorialComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [SqlInjectionTutorialComponent],
      imports: [
        RouterTestingModule,
        HttpClientModule,
        FormsModule,
        ReactiveFormsModule,
      ],
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(SqlInjectionTutorialComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
