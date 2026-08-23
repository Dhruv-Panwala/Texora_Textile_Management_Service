import React, { useEffect, useState } from 'react';
import { ImagePlus, Save, Trash } from 'lucide-react';
import { api } from '../services/api';
import { CompanyProfile } from '../types';

const emptyProfile = {
  tradeName: '',
  gstNo: '',
  phone: '',
  address: '',
  defaultBroker: '',
  defaultQuality: '',
};

function Company() {
  const [profile, setProfile] = useState(emptyProfile);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [logoPreviewUrl, setLogoPreviewUrl] = useState('');
  const [logoFile, setLogoFile] = useState<File | null>(null);
  const [logoSaving, setLogoSaving] = useState(false);
  const [logoError, setLogoError] = useState('');

  const load = async () => {
    setLoading(true);
    setError('');
    setLogoError('');
    const logoRequest = api.getCompanyLogo()
      .then((logo) => {
        setLogoPreviewUrl(logo ? URL.createObjectURL(logo) : '');
      })
      .catch(() => {
        setLogoPreviewUrl('');
        setLogoError('Company details loaded, but the logo could not be loaded. Re-upload or remove the logo before downloading bills or challans.');
      });
    try {
      const data = await api.getCompanyProfile();
      setProfile({
        tradeName: data.tradeName || '',
        gstNo: data.gstNo || '',
        phone: data.phone || '',
        address: data.address || '',
        defaultBroker: data.defaultBroker || '',
        defaultQuality: data.defaultQuality || '',
      });
      setLogoFile(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load company profile');
    } finally {
      setLoading(false);
    }
    await logoRequest;
  };

  useEffect(() => {
    load();
  }, []);

  useEffect(() => () => {
    if (logoPreviewUrl.startsWith('blob:')) {
      URL.revokeObjectURL(logoPreviewUrl);
    }
  }, [logoPreviewUrl]);

  const chooseLogo = (file: File | undefined) => {
    if (!file) {
      return;
    }
    if (!['image/png', 'image/jpeg'].includes(file.type) || file.size > 1024 * 1024) {
      setError('Choose a PNG or JPEG logo up to 1 MB.');
      return;
    }
    setError('');
    setLogoError('');
    setLogoFile(file);
    setLogoPreviewUrl(URL.createObjectURL(file));
  };

  const uploadLogo = async () => {
    if (!logoFile) {
      return;
    }
    setLogoSaving(true);
    setError('');
    setMessage('');
    try {
      await api.uploadCompanyLogo(logoFile);
      setLogoFile(null);
      await load();
      setMessage('Logo updated');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to upload logo');
    } finally {
      setLogoSaving(false);
    }
  };

  const removeLogo = async () => {
    setLogoSaving(true);
    setError('');
    setMessage('');
    try {
      await api.deleteCompanyLogo();
      setLogoFile(null);
      setLogoPreviewUrl('');
      setLogoError('');
      setMessage('Logo removed; the default logo will be used.');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to remove logo');
    } finally {
      setLogoSaving(false);
    }
  };

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setSaving(true);
    setError('');
    setMessage('');
    try {
      const payload: Partial<CompanyProfile> = {
        tradeName: profile.tradeName.trim(),
        gstNo: profile.gstNo.trim().toUpperCase(),
        phone: profile.phone.trim(),
        address: profile.address.trim(),
        defaultBroker: profile.defaultBroker.trim(),
        defaultQuality: profile.defaultQuality.trim(),
      };
      const updated = await api.updateCompanyProfile(payload);
      setProfile({
        tradeName: updated.tradeName || '',
        gstNo: updated.gstNo || '',
        phone: updated.phone || '',
        address: updated.address || '',
        defaultBroker: updated.defaultBroker || '',
        defaultQuality: updated.defaultQuality || '',
      });
      setMessage('Company profile updated');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to save company profile');
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return <div className="text-gray-600">Loading company profile...</div>;
  }

  return (
    <div className="page-shell">
      <div>
        <h1 className="page-title">Company Profile</h1>
        <p className="page-subtitle">Control the company details that appear across billing, challans, and daily operations.</p>
      </div>
      <form onSubmit={submit} className="form-surface max-w-4xl space-y-4">
        {error && <div className="text-sm text-red-700 bg-red-50 border border-red-200 rounded p-2">{error}</div>}
        {message && <div className="text-sm text-green-700 bg-green-50 border border-green-200 rounded p-2">{message}</div>}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <input
            required
            className="border rounded-md px-3 py-2"
            placeholder="Trade Name"
            value={profile.tradeName}
            onChange={(e) => setProfile({ ...profile, tradeName: e.target.value })}
          />
          <input
            className="border rounded-md px-3 py-2 uppercase"
            placeholder="GST No"
            maxLength={15}
            value={profile.gstNo}
            onChange={(e) => setProfile({ ...profile, gstNo: e.target.value.replace(/[^a-z0-9]/gi, '').toUpperCase() })}
          />
          <input
            className="border rounded-md px-3 py-2"
            placeholder="Phone"
            value={profile.phone}
            onChange={(e) => setProfile({ ...profile, phone: e.target.value })}
          />
          <input
            className="border rounded-md px-3 py-2"
            placeholder="Default Broker"
            value={profile.defaultBroker}
            onChange={(e) => setProfile({ ...profile, defaultBroker: e.target.value })}
          />
          <input
            className="border rounded-md px-3 py-2 md:col-span-2"
            placeholder="Default Quality"
            value={profile.defaultQuality}
            onChange={(e) => setProfile({ ...profile, defaultQuality: e.target.value })}
          />
          <textarea
            className="border rounded-md px-3 py-2 md:col-span-2"
            placeholder="Address"
            value={profile.address}
            onChange={(e) => setProfile({ ...profile, address: e.target.value })}
          />
        </div>
        <div>
          <button disabled={saving} className="btn-primary disabled:opacity-60">
            <Save className="w-4 h-4" /> Save Profile
          </button>
        </div>
      </form>
      <section className="form-surface max-w-4xl mt-4 space-y-4">
        <div>
          <h2 className="text-lg font-bold text-stone-900">Bill & Challan Logo</h2>
          <p className="text-sm text-stone-500">PNG or JPEG, up to 1 MB and 2000 x 2000 px. The PDF keeps the full logo inside the exact 72 x 72 pt header area.</p>
        </div>
        {logoError && <div role="alert" className="text-sm text-amber-800 bg-amber-50 border border-amber-200 rounded p-2">{logoError}</div>}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 items-start">
          {['Bill preview', 'Challan preview'].map((label) => (
            <div key={label}>
              <div className="text-xs font-semibold uppercase tracking-wide text-stone-500 mb-2">{label}</div>
              <div className="h-28 w-28 rounded-lg border border-dashed border-stone-300 bg-white flex items-center justify-center overflow-hidden">
                {logoPreviewUrl ? <img src={logoPreviewUrl} alt={`${label} logo preview`} className="max-h-full max-w-full object-contain" /> : <span className="text-xs text-stone-400 text-center px-2">Default logo</span>}
              </div>
              <div className="text-xs text-stone-400 mt-1">Full image contained; no crop.</div>
            </div>
          ))}
        </div>
        <div className="flex flex-wrap gap-2 items-center">
          <label className="btn-secondary cursor-pointer">
            <ImagePlus className="w-4 h-4" /> Choose logo
            <input type="file" className="hidden" accept="image/png,image/jpeg" onChange={(event) => chooseLogo(event.target.files?.[0])} />
          </label>
          <button type="button" disabled={!logoFile || logoSaving} onClick={uploadLogo} className="btn-primary disabled:opacity-50">Upload logo</button>
          <button type="button" disabled={!logoPreviewUrl || logoSaving} onClick={removeLogo} className="btn-secondary disabled:opacity-50"><Trash className="w-4 h-4" /> Remove</button>
          {logoFile && <span className="text-sm text-stone-500">{logoFile.name}</span>}
        </div>
      </section>
    </div>
  );
}

export default Company;
