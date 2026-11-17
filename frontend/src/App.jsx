import { useEffect, useState } from "react";

async function api(path, body) {
  const options =
    body === undefined
      ? {}
      : {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(body),
        };
  const res = await fetch("/api" + path, options);
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.message || "Something went wrong");
  return data;
}

export default function App() {
  const [config, setConfig] = useState({ demo: false, cities: [] });
  const [user, setUser] = useState(null);
  const [ready, setReady] = useState(false);
  const [phone, setPhone] = useState("");

  useEffect(() => {
    api("/config").then(setConfig).catch(() => {});
    api("/me")
      .then(setUser)
      .catch(() => {})
      .finally(() => setReady(true));
  }, []);

  if (!ready) return null;

  return (
    <div className="page">
      <header>
        <span className="logo">✈ SkyGuard</span>
        <span className="tag">Loyalty</span>
      </header>
      <div className="layout">
        {user ? (
          <Home user={user} setUser={setUser} />
        ) : (
          <Login config={config} phone={phone} setPhone={setPhone} setUser={setUser} />
        )}
        {config.demo && <Phone phone={user ? user.phone : phone} />}
      </div>
    </div>
  );
}

function Login({ config, phone, setPhone, setUser }) {
  const [city, setCity] = useState("Mumbai");
  const [isNew, setIsNew] = useState(false);
  const [name, setName] = useState("");
  const [sent, setSent] = useState(false);
  const [code, setCode] = useState("");
  const [msg, setMsg] = useState("");

  async function sendCode(e) {
    e.preventDefault();
    setMsg("");
    try {
      await api("/otp/send", { phone, city, name: isNew ? name : "" });
      setSent(true);
    } catch (err) {
      setMsg(err.message);
    }
  }

  async function verify(e) {
    e.preventDefault();
    setMsg("");
    try {
      setUser(await api("/otp/verify", { phone, code }));
    } catch (err) {
      setMsg(err.message);
    }
  }

  return (
    <div className="card">
      <h2>{sent ? "Enter the code" : "Welcome aboard"}</h2>
      {msg && <p className="msg bad">{msg}</p>}
      {config.cities.length === 0 && (
        <p className="msg bad">Risk checker is not running. Start it with: python3 risk_advisor/app.py</p>
      )}

      {!sent ? (
        <form onSubmit={sendCode}>
          <label>Mobile number</label>
          <input value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="9999999999" maxLength={10} />

          <label className="check">
            <input type="checkbox" checked={isNew} onChange={(e) => setIsNew(e.target.checked)} />
            I am new here
          </label>
          {isNew && (
            <>
              <label>Your name</label>
              <input value={name} onChange={(e) => setName(e.target.value)} />
            </>
          )}

          <label>{isNew ? "Your home city" : "I am logging in from"}</label>
          <select value={city} onChange={(e) => setCity(e.target.value)}>
            {config.cities.map((c) => (
              <option key={c}>{c}</option>
            ))}
          </select>
          <button className="main">Send code by SMS</button>
          <p className="hint">Demo member: 9999999999 (home city Mumbai). Pick a far away city to see the extra checks.</p>
        </form>
      ) : (
        <form onSubmit={verify}>
          <p className="hint">We sent a 6 digit code to {phone}.</p>
          <input className="big" value={code} onChange={(e) => setCode(e.target.value)} maxLength={6} placeholder="------" />
          <button className="main">Verify and log in</button>
          <button type="button" className="link" onClick={() => setSent(false)}>
            Change number
          </button>
        </form>
      )}
    </div>
  );
}

function Home({ user, setUser }) {
  const [items, setItems] = useState([]);
  const [amount, setAmount] = useState("");
  const [msg, setMsg] = useState(null);
  const [faceId, setFaceId] = useState(null);
  const [codeSent, setCodeSent] = useState(false);
  const [code, setCode] = useState("");

  function load() {
    api("/inbox").then(setItems).catch(() => {});
    api("/me").then(setUser).catch(() => {});
  }
  useEffect(load, []);

  async function run(action, okText) {
    try {
      await action();
      setMsg({ text: okText, ok: true });
    } catch (err) {
      setMsg({ text: err.message, ok: false });
    }
    load();
  }

  function redeem(e) {
    e.preventDefault();
    run(() => api("/redeem", { amount: Number(amount) }), "Request sent. Approve it in your inbox below.");
    setAmount("");
  }

  function approve(item) {
    if (item.needsFace) setFaceId(item.id);
    else run(() => api("/inbox/" + item.id + "/approve", {}), "Points redeemed.");
  }

  function faceResult(result) {
    const id = faceId;
    setFaceId(null);
    run(() => api("/inbox/" + id + "/face", { result }), "Face matched. Points redeemed.");
  }

  function sendUnfreezeCode() {
    run(async () => {
      await api("/unfreeze/send", {});
      setCodeSent(true);
    }, "Code sent to your phone.");
  }

  function unfreeze() {
    run(async () => {
      await api("/unfreeze", { code });
      setCodeSent(false);
      setCode("");
    }, "Account unfrozen.");
  }

  async function logout() {
    await api("/logout", {});
    setUser(null);
  }

  return (
    <div className="main-col">
      <div className="card balance">
        <div className="row">
          <div>
            <p className="small">Hello, {user.name}</p>
            <h1>{user.points.toLocaleString()} <span>points</span></h1>
          </div>
          <span className={"chip " + (user.frozen ? "declined" : "approved")}>{user.frozen ? "Frozen" : "Active"}</span>
        </div>
        <p className="small">Home city {user.homeCity} · logged in from {user.loginCity}</p>
        <button className="link" onClick={logout}>Log out</button>
      </div>

      {msg && <p className={"msg " + (msg.ok ? "good" : "bad")}>{msg.text}</p>}

      {user.frozen ? (
        <div className="card warn">
          <h3>Your account is frozen</h3>
          <p className="hint">Nothing can be redeemed. To unfreeze, we will send a code to your phone.</p>
          {!codeSent ? (
            <button className="main" onClick={sendUnfreezeCode}>Send unfreeze code</button>
          ) : (
            <div className="row">
              <input value={code} onChange={(e) => setCode(e.target.value)} maxLength={6} placeholder="6 digit code" />
              <button className="main" onClick={unfreeze}>Unfreeze</button>
            </div>
          )}
        </div>
      ) : (
        <form className="card" onSubmit={redeem}>
          <h3>Redeem points</h3>
          <div className="row">
            <input type="number" min="1" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="How many points?" />
            <button className="main">Redeem</button>
          </div>
          <p className="hint">Big amounts and unusual logins need a face unlock too.</p>
        </form>
      )}

      <div className="card">
        <h3>My inbox</h3>
        {items.length === 0 && <p className="hint">No requests yet.</p>}
        {items.map((item) => (
          <div className="item" key={item.id}>
            <div>
              <b>{item.amount.toLocaleString()} points</b>
              <span className={"chip " + item.status.toLowerCase()}>{item.status}</span>
              <p className="hint">{item.reason}</p>
            </div>
            {item.status === "PENDING" && (
              <div className="row">
                <button className="main small-btn" onClick={() => approve(item)}>
                  {item.needsFace ? "Approve with face" : "Approve"}
                </button>
                <button className="link" onClick={() => run(() => api("/inbox/" + item.id + "/decline", {}), "Request declined.")}>
                  Decline
                </button>
              </div>
            )}
          </div>
        ))}
      </div>

      {!user.frozen && (
        <div className="card">
          <h3>Not feeling safe?</h3>
          <p className="hint">Freeze your account straight away. You can unfreeze it later with an SMS code.</p>
          <button className="danger" onClick={() => run(() => api("/freeze", {}), "Account frozen. We sent you an SMS.")}>
            Freeze my account
          </button>
        </div>
      )}

      {faceId && (
        <div className="overlay">
          <div className="card">
            <h3>Face unlock</h3>
            <p className="hint">This step is a placeholder. Real face unlock will replace these two buttons.</p>
            <div className="row">
              <button className="main" onClick={() => faceResult("pass")}>Face matches</button>
              <button className="danger" onClick={() => faceResult("fail")}>Face fails</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function Phone({ phone }) {
  const [texts, setTexts] = useState([]);

  useEffect(() => {
    if (!/^[6-9][0-9]{9}$/.test(phone)) {
      setTexts([]);
      return;
    }
    const get = () => api("/demo/sms?phone=" + phone).then(setTexts).catch(() => {});
    get();
    const timer = setInterval(get, 2000);
    return () => clearInterval(timer);
  }, [phone]);

  function withLinks(text) {
    return text.split(/(http\S+)/).map((part, i) =>
      part.startsWith("http") ? (
        <a key={i} href={part} target="_blank" rel="noreferrer">{part}</a>
      ) : (
        part
      )
    );
  }

  return (
    <aside className="phone">
      <h4>Demo phone</h4>
      <p className="small">{phone ? "SMS for +91 " + phone : "Type a number to see its SMS"}</p>
      {texts.map((t, i) => (
        <div className="sms" key={i}>{withLinks(t)}</div>
      ))}
      {texts.length === 0 && <p className="hint">No messages yet.</p>}
    </aside>
  );
}
