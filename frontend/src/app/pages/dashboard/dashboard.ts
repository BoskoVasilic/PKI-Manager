import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AuthService } from '../../services/auth.service';

export type UserRole = 'ADMIN' | 'CA_USER' | 'USER';

interface NavItem {
  label: string;
  icon: string;
  route: string;
  description: string;
  tag?: string;
}

interface DashboardConfig {
  roleLabel: string;
  roleColor: string;
  greeting: string;
  navItems: NavItem[];
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './dashboard.html'
})
export class DashboardComponent implements OnInit {
  userEmail = '';
  userName = '';
  userRole: UserRole = 'USER';
  config!: DashboardConfig;

  private readonly roleConfigs: Record<UserRole, DashboardConfig> = {
    ADMIN: {
      roleLabel: 'Administrator',
      roleColor: 'gold',
      greeting: 'Full system access',
      navItems: [
        {
          label: 'Issue Certificate',
          icon: 'cert',
          route: '/certificates/issue',
          description: 'Issue Root, Intermediate or End-Entity certificates',
          tag: 'Root / Intermediate / EE',
        },
        {
          label: 'All Certificates',
          icon: 'list',
          route: '/certificates',
          description: 'View, download or revoke any certificate in the system',
        },
        {
          label: 'Manage CA Users',
          icon: 'users',
          route: '/admin/ca-users',
          description: 'Add new organizations and CA users',
          tag: 'Func. 2',
        },
        {
          label: 'Revocation (CRL)',
          icon: 'revoke',
          route: '/certificates/revoked',
          description: 'View the certificate revocation list',
          tag: 'Func. 10',
        },
      ],
    },
    CA_USER: {
      roleLabel: 'CA User',
      roleColor: 'blue',
      greeting: 'Organization access',
      navItems: [
        {
          label: 'Issue Certificate',
          icon: 'cert',
          route: '/certificates/issue',
          description: 'Issue Intermediate or EE certs for your organization',
          tag: 'Intermediate / EE',
        },
        {
          label: 'My Organization Certs',
          icon: 'list',
          route: '/certificates',
          description: 'View and download certificates from your organization',
        },
        {
          label: 'Download Certificate',
          icon: 'download',
          route: '/certificates/download',
          description: 'Download certificates with or without private key',
          tag: 'Func. 7',
        },
      ],
    },
    USER: {
      roleLabel: 'User',
      roleColor: 'green',
      greeting: 'Personal certificate access',
      navItems: [
        {
          label: 'Upload CSR',
          icon: 'upload',
          route: '/certificates/csr',
          description: 'Upload a CSR file to request a certificate from a CA',
          tag: 'Func. 8',
        },
        {
          label: 'Generate Certificate',
          icon: 'cert',
          route: '/certificates/generate',
          description: 'Auto-generate key pair and certificate via PKI',
          tag: 'Func. 8',
        },
        {
          label: 'My Certificates',
          icon: 'list',
          route: '/certificates',
          description: 'View and download your End-Entity certificates',
        },
        {
          label: 'Revoke Certificate',
          icon: 'revoke',
          route: '/certificates/revoke',
          description: 'Revoke one of your certificates with an X.509 reason',
          tag: 'Func. 10',
        },
      ],
    },
  };

  constructor(private authService: AuthService) {}

  ngOnInit(): void {
    this.decodeToken();
    this.config = this.roleConfigs[this.userRole];
  }

  logout(): void {
    this.authService.logout();
  }

  private decodeToken(): void {
    const token = this.authService.getToken();
    if (!token) return;

    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      this.userEmail = payload.sub || payload.email || '';
      this.userName = payload.name || this.userEmail.split('@')[0] || 'User';

      // Adjust this key to match whatever claim your Spring Boot JWT uses
      const role = payload.role || payload.roles?.[0] || 'USER';
      this.userRole = role.replace('ROLE_', '') as UserRole;
    } catch {
      this.userRole = 'USER';
    }
  }

  getRoleBadgeClass(): string {
    const map: Record<string, string> = {
        gold:  'bg-yellow-400/10 text-yellow-400 border border-yellow-400/25',
        blue:  'bg-blue-400/10  text-blue-400  border border-blue-400/25',
        green: 'bg-emerald-400/10 text-emerald-400 border border-emerald-400/25',
    };
    return map[this.config.roleColor] ?? '';
}

getIconWrapClass(): string {
    const map: Record<string, string> = {
        gold:  'bg-yellow-400/10 text-yellow-400',
        blue:  'bg-blue-400/10  text-blue-400',
        green: 'bg-emerald-400/10 text-emerald-400',
    };
    return map[this.config.roleColor] ?? '';
}
}