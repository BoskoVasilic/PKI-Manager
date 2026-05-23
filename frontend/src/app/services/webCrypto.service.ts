import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class WebCryptoService {

  async decryptChallenge(encryptedBase64: string, privateKeyPem: string): Promise<string> {
    try {
      const privateKey = await this.importPrivateKey(privateKeyPem);

      const encryptedBytes = this.base64ToArrayBuffer(encryptedBase64);

      const decryptedBuffer = await window.crypto.subtle.decrypt(
        { name: 'RSA-OAEP' },
        privateKey,
        encryptedBytes
      );

      return new TextDecoder().decode(decryptedBuffer);

    } catch (error) {
      throw new Error('Decryption failed. Check if you uploaded the correct private key.');
    }
  }

  async importPrivateKey(pem: string): Promise<CryptoKey> {
    const cleaned = pem
      .replace('-----BEGIN PRIVATE KEY-----', '')
      .replace('-----END PRIVATE KEY-----', '')
      .replace('-----BEGIN RSA PRIVATE KEY-----', '')
      .replace('-----END RSA PRIVATE KEY-----', '')
      .replace(/\s+/g, '');

    const keyBuffer = this.base64ToArrayBuffer(cleaned);

    return window.crypto.subtle.importKey(
      'pkcs8',
      keyBuffer,
      {
        name: 'RSA-OAEP',
        hash: 'SHA-256',
      },
      false,
      ['decrypt']
    );
  }


  async importPublicKey(pem: string): Promise<CryptoKey> {
    const cleaned = pem
      .replace('-----BEGIN PUBLIC KEY-----', '')
      .replace('-----END PUBLIC KEY-----', '')
      .replace(/\s+/g, '');

    const keyBuffer = this.base64ToArrayBuffer(cleaned);

    return window.crypto.subtle.importKey(
      'spki',
      keyBuffer,
      { name: 'RSA-OAEP', hash: 'SHA-256' },
      false,
      ['encrypt']
    );
  }

  async validatePublicKey(pem: string): Promise<boolean> {
    if (!pem || !pem.includes('-----BEGIN PUBLIC KEY-----')) return false;
    try {
      await this.importPublicKey(pem);
      return true;
    } catch {
      return false;
    }
  }

  async validatePrivateKey(pem: string): Promise<boolean> {
    if (!pem) return false;
    if (!pem.includes('-----BEGIN PRIVATE KEY-----') &&
      !pem.includes('-----BEGIN RSA PRIVATE KEY-----')) return false;
    try {
      await this.importPrivateKey(pem);
      return true;
    } catch {
      return false;
    }
  }

  private base64ToArrayBuffer(base64: string): ArrayBuffer {
    const binaryString = window.atob(base64);
    const bytes = new Uint8Array(binaryString.length);
    for (let i = 0; i < binaryString.length; i++) {
      bytes[i] = binaryString.charCodeAt(i);
    }
    return bytes.buffer;
  }
}
