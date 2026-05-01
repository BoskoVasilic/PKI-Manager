import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminCertificatesViewComponent } from './admin-certificates-view.component';

describe('AdminCertificatesViewComponent', () => {
  let component: AdminCertificatesViewComponent;
  let fixture: ComponentFixture<AdminCertificatesViewComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminCertificatesViewComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AdminCertificatesViewComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
