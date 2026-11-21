import { describe, it, expect, beforeEach, vi } from 'vitest';
import { hashToken, generateSecureToken, generateInvitationCode, generateRecoveryCode } from './middleware/auth';

describe('Auth Utilities', () => {
  describe('hashToken', () => {
    it('should produce consistent hashes for same input', async () => {
      const token = 'test-token-123';
      const hash1 = await hashToken(token);
      const hash2 = await hashToken(token);
      expect(hash1).toBe(hash2);
    });

    it('should produce different hashes for different inputs', async () => {
      const hash1 = await hashToken('token-1');
      const hash2 = await hashToken('token-2');
      expect(hash1).not.toBe(hash2);
    });

    it('should produce 64-character hex string', async () => {
      const hash = await hashToken('test');
      expect(hash).toHaveLength(64);
      expect(hash).toMatch(/^[0-9a-f]+$/);
    });
  });

  describe('generateSecureToken', () => {
    it('should generate token of specified length', () => {
      const token = generateSecureToken(32);
      expect(token).toHaveLength(64); // 32 bytes = 64 hex chars
    });

    it('should generate different tokens each call', () => {
      const tokens = new Set();
      for (let i = 0; i < 100; i++) {
        tokens.add(generateSecureToken(16));
      }
      expect(tokens.size).toBe(100);
    });

    it('should only contain hex characters', () => {
      const token = generateSecureToken(16);
      expect(token).toMatch(/^[0-9a-f]+$/);
    });
  });

  describe('generateInvitationCode', () => {
    it('should generate 6-digit code', () => {
      const code = generateInvitationCode();
      expect(code).toHaveLength(6);
      expect(code).toMatch(/^\d{6}$/);
    });

    it('should generate different codes', () => {
      const codes = new Set();
      for (let i = 0; i < 1000; i++) {
        codes.add(generateInvitationCode());
      }
      expect(codes.size).toBeGreaterThan(900); // Allow small collision chance
    });
  });

  describe('generateRecoveryCode', () => {
    it('should generate 8-character code', () => {
      const code = generateRecoveryCode();
      expect(code).toHaveLength(8);
    });

    it('should not contain confusing characters', () => {
      const confusing = 'IO01l';
      for (let i = 0; i < 100; i++) {
        const code = generateRecoveryCode();
        for (const char of confusing) {
          expect(code).not.toContain(char);
        }
      }
    });

    it('should only contain allowed characters', () => {
      const allowed = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
      for (let i = 0; i < 100; i++) {
        const code = generateRecoveryCode();
        for (const char of code) {
          expect(allowed).toContain(char);
        }
      }
    });
  });
});
