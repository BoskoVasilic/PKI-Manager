import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import {CaUserRegisterComponent} from './components/ca-user-register/ca-user-register.component';

@Component({
  selector: 'app-root',
  imports: [CaUserRegisterComponent],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  protected readonly title = signal('pk-infrastructure');
}
