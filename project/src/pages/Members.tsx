import { useEffect, useMemo, useState } from 'react';
import { Copy, UserPlus, UserRoundMinus } from 'lucide-react';
import { api } from '../services/api';
import type { InvitationCreated, WorkspaceMember, WorkspaceRole, WorkspaceSummary } from '../types';

const roles: Array<Exclude<WorkspaceRole, 'OWNER'>> = ['ADMIN', 'MANAGER', 'STAFF', 'ACCOUNTANT', 'VIEWER'];

function Members() {
  const [workspaces, setWorkspaces] = useState<WorkspaceSummary[]>([]);
  const [workspaceId, setWorkspaceId] = useState<number | null>(null);
  const [members, setMembers] = useState<WorkspaceMember[]>([]);
  const [membersPage, setMembersPage] = useState(0);
  const [membersTotalPages, setMembersTotalPages] = useState(0);
  const [membersTotalElements, setMembersTotalElements] = useState(0);
  const [email, setEmail] = useState('');
  const [role, setRole] = useState<Exclude<WorkspaceRole, 'OWNER'>>('STAFF');
  const [invitation, setInvitation] = useState<InvitationCreated | null>(null);
  const [loading, setLoading] = useState(true);
  const [membersLoading, setMembersLoading] = useState(true);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  const workspace = useMemo(() => workspaces.find((item) => item.id === workspaceId), [workspaces, workspaceId]);
  const canManage = workspace?.role === 'OWNER' || workspace?.role === 'ADMIN';

  useEffect(() => {
    api.getWorkspaces()
      .then((loaded) => {
        setWorkspaces(loaded);
        setWorkspaceId(loaded[0]?.id || null);
      })
      .catch((err) => setError(err instanceof Error ? err.message : 'Failed to load workspaces'))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    if (workspaceId == null) {
      return;
    }
    setMembersLoading(true);
    api.getWorkspaceMembers(workspaceId, membersPage)
      .then((result) => {
        setMembers(result.content);
        setMembersTotalPages(result.totalPages);
        setMembersTotalElements(result.totalElements);
      })
      .catch((err) => setError(err instanceof Error ? err.message : 'Failed to load members'))
      .finally(() => setMembersLoading(false));
  }, [workspaceId, membersPage]);

  const invite = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!workspaceId) return;
    setError('');
    setMessage('');
    try {
      const created = await api.inviteWorkspaceMember(workspaceId, email.trim(), role);
      setInvitation(created);
      setEmail('');
      setMessage(`Invitation email sent to ${created.email} with the ${created.role} role. You can also copy the link below.`);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create invitation');
    }
  };

  const changeRole = async (member: WorkspaceMember, nextRole: WorkspaceRole) => {
    if (!workspaceId || nextRole === 'OWNER') return;
    try {
      await api.updateWorkspaceMemberRole(workspaceId, member.userId, nextRole);
      setMembers((current) => current.map((item) => item.userId === member.userId
        ? { ...item, role: nextRole }
        : item));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to update role');
    }
  };

  const remove = async (member: WorkspaceMember) => {
    if (!workspaceId || !window.confirm(`Remove ${member.displayName || member.email || member.username}?`)) return;
    try {
      await api.removeWorkspaceMember(workspaceId, member.userId);
      const nextTotal = Math.max(0, membersTotalElements - 1);
      setMembers((current) => current.filter((item) => item.userId !== member.userId));
      setMembersTotalElements(nextTotal);
      setMembersTotalPages(Math.ceil(nextTotal / 25));
      if (membersPage > 0 && members.length <= 1) {
        setMembersPage((current) => Math.max(0, current - 1));
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to remove member');
    }
  };

  const invitationLink = invitation
    ? `${window.location.origin}/accept-invitation?token=${encodeURIComponent(invitation.token)}`
    : '';

  const copyInvitationLink = async () => {
    setError('');
    try {
      await navigator.clipboard.writeText(invitationLink);
      setMessage('Invitation link copied.');
    } catch {
      setError('Could not copy the invitation link. Select and copy it manually.');
    }
  };

  if (loading) return <div className="text-gray-600">Loading workspace members...</div>;

  return (
    <div className="page-shell space-y-5">
      <div>
        <h1 className="page-title">Workspace Members</h1>
        <p className="page-subtitle">Invite people and control their access across the businesses in this workspace.</p>
      </div>

      {error && <div className="rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
      {message && <div className="rounded-2xl border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-700">{message}</div>}

      {workspaces.length > 1 && (
        <select className="max-w-md border rounded-md px-3 py-2" value={workspaceId || ''} onChange={(event) => { setWorkspaceId(Number(event.target.value)); setMembersPage(0); }}>
          {workspaces.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
        </select>
      )}

      {canManage && workspace && (
        <form onSubmit={invite} className="form-surface max-w-4xl flex flex-wrap gap-3 items-end">
          <div className="min-w-[240px] flex-1"><label className="text-sm font-semibold text-stone-700">Email</label><input required type="email" className="mt-1 w-full border rounded-md px-3 py-2" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="person@example.com" /></div>
          <div><label className="text-sm font-semibold text-stone-700">Role</label><select className="mt-1 border rounded-md px-3 py-2" value={role} onChange={(event) => setRole(event.target.value as typeof role)}>{roles.map((item) => <option key={item}>{item}</option>)}</select></div>
          <button className="btn-primary"><UserPlus className="h-4 w-4" /> Create Invitation</button>
        </form>
      )}

      {invitation && <div className="form-surface max-w-4xl"><div className="text-sm font-semibold text-stone-700">Invitation link</div><div className="mt-2 flex gap-2"><input readOnly className="min-w-0 flex-1 border rounded-md px-3 py-2 text-sm" value={invitationLink} /><button type="button" className="btn-secondary" onClick={() => void copyInvitationLink()}><Copy className="h-4 w-4" /> Copy</button></div><p className="mt-2 text-xs text-stone-500">This link expires in 7 days.</p></div>}

      <div className="form-surface max-w-4xl">
        <h2 className="text-lg font-bold text-stone-900">Role access</h2>
        <p className="mt-1 text-sm text-stone-500">Current permissions across the workspace.</p>
        <div className="mt-4 overflow-x-auto">
          <table className="data-table"><thead><tr><th>Role</th><th>Access</th></tr></thead><tbody>
            <tr><td>OWNER</td><td>Full access, including companies, members, invitations, and all records.</td></tr>
            <tr><td>ADMIN</td><td>Full operational access, plus companies, members, and invitations.</td></tr>
            <tr><td>MANAGER</td><td>Create, edit, delete, and manage sales, purchases, parties, payments, and documents.</td></tr>
            <tr><td>STAFF</td><td>Create, edit, delete, and manage operational records and documents.</td></tr>
            <tr><td>ACCOUNTANT</td><td>Create, edit, delete, and manage operational records, payments, and documents.</td></tr>
            <tr><td>VIEWER</td><td>View, export, and download records only; cannot add, edit, delete, pay, invite, or manage companies.</td></tr>
          </tbody></table>
        </div>
      </div>

      <div className="table-surface overflow-x-auto"><table className="data-table"><thead><tr><th>Member</th><th>Email</th><th>Role</th><th>Joined</th><th>Actions</th></tr></thead><tbody>{membersLoading && members.length === 0 ? <tr><td colSpan={5} className="p-6 text-sm text-stone-500">Loading members...</td></tr> : members.map((member) => <tr key={member.userId}><td>{member.displayName || member.username}</td><td>{member.email || member.username}</td><td>{canManage && member.role !== 'OWNER' ? <select className="border rounded-md px-2 py-1" value={member.role} onChange={(event) => changeRole(member, event.target.value as WorkspaceRole)}>{roles.map((item) => <option key={item}>{item}</option>)}</select> : member.role}</td><td>{member.createdAt ? new Date(member.createdAt).toLocaleDateString() : '-'}</td><td>{canManage && member.role !== 'OWNER' && <button title="Remove member" className="text-red-600 hover:text-red-800" onClick={() => void remove(member)}><UserRoundMinus className="h-5 w-5" /></button>}</td></tr>)}</tbody></table>{membersTotalPages > 1 && <div className="flex items-center justify-between p-4 text-sm text-stone-600"><button type="button" className="btn-secondary" disabled={membersPage === 0 || membersLoading} onClick={() => setMembersPage((current) => current - 1)}>Previous</button><span>Page {membersPage + 1} of {membersTotalPages}</span><button type="button" className="btn-secondary" disabled={membersPage + 1 >= membersTotalPages || membersLoading} onClick={() => setMembersPage((current) => current + 1)}>Next</button></div>}</div>
    </div>
  );
}

export default Members;
