import { request } from './request';
import type { LoginResponse, TokenRefreshResponse, User, LoginRequest, RegisterRequest, ChangePasswordRequest } from '../types/auth';

export const authApi = {
  login(data: LoginRequest): Promise<LoginResponse> {
    return request.post('/api/auth/login', data);
  },

  register(data: RegisterRequest): Promise<LoginResponse> {
    return request.post('/api/auth/register', data);
  },

  refreshToken(refreshToken: string): Promise<TokenRefreshResponse> {
    return request.post('/api/auth/refresh', { refreshToken });
  },

  logout(): Promise<void> {
    return request.post('/api/auth/logout');
  },

  getCurrentUser(): Promise<User> {
    return request.get('/api/auth/me');
  },

  changePassword(data: ChangePasswordRequest): Promise<void> {
    return request.put('/api/auth/password', data);
  },
};
