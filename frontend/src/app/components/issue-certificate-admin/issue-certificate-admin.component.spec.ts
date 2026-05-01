import { ComponentFixture, TestBed } from '@angular/core/testing';

import { IssueCertificateAdminComponent } from './issue-certificate-admin.component';

describe('IssueCertificateAdminComponent', () => {
  let component: IssueCertificateAdminComponent;
  let fixture: ComponentFixture<IssueCertificateAdminComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [IssueCertificateAdminComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(IssueCertificateAdminComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
