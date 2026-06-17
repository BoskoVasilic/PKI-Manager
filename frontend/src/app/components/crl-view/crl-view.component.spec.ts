import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CrlViewComponent } from './crl-view.component';

describe('CrlViewComponent', () => {
  let component: CrlViewComponent;
  let fixture: ComponentFixture<CrlViewComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CrlViewComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(CrlViewComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
