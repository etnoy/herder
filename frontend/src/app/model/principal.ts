import { User } from './user';

export class Principal {
  id: string;
  principalType: string;
  members: User[];
  displayName: string;
}
