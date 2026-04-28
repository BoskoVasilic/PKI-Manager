import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CaUserRegisterComponent } from './ca-user-register.component';

describe('CaUserRegisterComponent', () => {
  let component: CaUserRegisterComponent;
  let fixture: ComponentFixture<CaUserRegisterComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CaUserRegisterComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(CaUserRegisterComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
