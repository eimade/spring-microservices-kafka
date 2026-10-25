import { LitElement, html, css } from 'https://cdn.jsdelivr.net/npm/lit@3/+esm';

// URL du service consumer (vue lecture MongoDB)
const READ_URL = window.UIMAD_READ_URL || 'http://localhost:18301';

class UimadRef extends LitElement {
  static properties = {
    session: { state: true }, items: { state: true }, view: { state: true },
    error: { state: true }, history: { state: true },
  };

  static styles = css`
    :host { display: block; }
    .card { background: #fff; border-radius: 8px; padding: 16px; margin-bottom: 16px; box-shadow: 0 1px 3px #0002; }
    table { width: 100%; border-collapse: collapse; }
    th, td { text-align: left; padding: 6px 8px; border-bottom: 1px solid #e3e8ee; }
    .badge { padding: 2px 8px; border-radius: 10px; font-size: 12px; }
    .DRAFT { background: #ffe9b8; } .VALIDATED { background: #c9f0d2; }
    .error { color: #b00020; } input, select, button { padding: 6px 10px; margin: 2px; }
    nav button.active { font-weight: bold; }
  `;

  constructor() {
    super();
    this.session = JSON.parse(sessionStorage.getItem('session') || 'null');
    this.items = []; this.view = 'referentiel'; this.error = ''; this.history = null;
    if (this.session) this.load();
  }

  get headers() {
    return { 'Content-Type': 'application/json', Authorization: `Bearer ${this.session.token}` };
  }

  async call(url, options = {}) {
    this.error = '';
    const res = await fetch(url, { ...options, headers: this.session ? this.headers : options.headers });
    if (res.status === 401) { this.logout(); throw new Error('Session expirée'); }
    if (!res.ok) {
      const problem = await res.json().catch(() => ({}));
      const details = problem.errors ? Object.values(problem.errors).join(', ') : '';
      this.error = `${problem.detail || res.statusText} ${details}`;
      throw new Error(this.error);
    }
    return res.status === 204 ? null : res.json();
  }

  async login(e) {
    e.preventDefault();
    const form = new FormData(e.target);
    try {
      this.session = await this.call('/api/auth/login', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: form.get('username'), password: form.get('password') }),
      });
      sessionStorage.setItem('session', JSON.stringify(this.session));
      this.load();
    } catch { /* message affiché via this.error */ }
  }

  logout() { sessionStorage.removeItem('session'); this.session = null; this.items = []; }

  async load() {
    try {
      this.items = this.view === 'referentiel'
        ? await this.call('/api/references')
        : await this.call(`${READ_URL}/api/read/references`);
    } catch { /* erreur déjà affichée */ }
  }

  async show(view) { this.view = view; this.history = null; await this.load(); }

  async validate(id) { await this.call(`/api/references/${id}/validate`, { method: 'POST' }).catch(() => {}); this.load(); }
  async remove(id) { await this.call(`/api/references/${id}`, { method: 'DELETE' }).catch(() => {}); this.load(); }
  async showHistory(id) { this.history = await this.call(`/api/references/${id}/history`).catch(() => null); }

  async create(e) {
    e.preventDefault();
    const f = new FormData(e.target);
    try {
      await this.call('/api/references', { method: 'POST', body: JSON.stringify(Object.fromEntries(f)) });
      e.target.reset(); this.load();
    } catch { /* erreur affichée */ }
  }

  hasRole(role) { return this.session?.roles.includes(role); }

  render() {
    if (!this.session) {
      return html`<div class="card"><form @submit=${this.login}>
        <h3>Connexion</h3>
        <input name="username" placeholder="Utilisateur (editeur / validateur)" required>
        <input name="password" type="password" placeholder="Mot de passe" required>
        <button>Se connecter</button>
        <p class="error">${this.error}</p></form></div>`;
    }
    const writable = this.view === 'referentiel';
    return html`
      <div class="card">Connecté : <b>${this.session.username}</b> (${this.session.roles.join(', ')})
        <button @click=${this.logout}>Déconnexion</button></div>
      <nav>
        <button class=${this.view === 'referentiel' ? 'active' : ''} @click=${() => this.show('referentiel')}>Référentiel (PostgreSQL)</button>
        <button class=${this.view === 'lecture' ? 'active' : ''} @click=${() => this.show('lecture')}>Vue lecture (MongoDB via Kafka)</button>
      </nav>
      <p class="error">${this.error}</p>
      ${writable && this.hasRole('EDITOR') ? html`<div class="card"><form @submit=${this.create}>
        <select name="type"><option>PRODUIT</option><option>FOURNISSEUR</option></select>
        <input name="code" placeholder="Code" required>
        <input name="label" placeholder="Libellé" required>
        <input name="description" placeholder="Description">
        <button>Créer (brouillon)</button></form></div>` : ''}
      <div class="card"><table>
        <tr><th>Type</th><th>Code</th><th>Libellé</th><th>Statut</th><th>Version</th><th></th></tr>
        ${this.items.map(i => html`<tr>
          <td>${i.type}</td><td>${i.code}</td><td>${i.label}</td>
          <td><span class="badge ${i.status}">${i.status}</span></td><td>v${i.version}</td>
          <td>${writable ? html`
            <button @click=${() => this.showHistory(i.id)}>Historique</button>
            ${i.status === 'DRAFT' && this.hasRole('VALIDATOR') ? html`<button @click=${() => this.validate(i.id)}>Valider</button>` : ''}
            ${this.hasRole('EDITOR') ? html`<button @click=${() => this.remove(i.id)}>Supprimer</button>` : ''}` : ''}</td>
        </tr>`)}
      </table></div>
      ${this.history ? html`<div class="card"><h4>Historique des versions</h4><ul>
        ${this.history.map(h => html`<li>v${h.version} - ${h.action} par ${h.changedBy} (${h.status}) : ${h.label}</li>`)}
      </ul></div>` : ''}`;
  }
}
customElements.define('uimad-ref', UimadRef);
