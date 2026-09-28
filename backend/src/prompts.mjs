/** Starter templates shown in every user's prompt library. `{{input}}` marks where the user's text goes. */
export const builtInPrompts = Object.freeze([
  { id: 'tpl-summarize', title: 'Summarize', category: 'Writing', content: 'Summarize the following in 5 bullet points, then give a one-sentence takeaway:\n\n{{input}}' },
  { id: 'tpl-explain', title: 'Explain simply', category: 'Learning', content: 'Explain this like I am new to the topic. Use an analogy and a short example:\n\n{{input}}' },
  { id: 'tpl-email', title: 'Professional email', category: 'Writing', content: 'Write a clear, friendly, professional email. Keep it under 150 words. Context:\n\n{{input}}' },
  { id: 'tpl-rewrite', title: 'Improve writing', category: 'Writing', content: 'Improve the clarity, grammar and flow of this text while keeping my voice. Show the rewritten version, then list the main changes:\n\n{{input}}' },
  { id: 'tpl-code-review', title: 'Review code', category: 'Code', content: 'Review this code for bugs, security issues and readability. List problems by severity with a suggested fix for each:\n\n```\n{{input}}\n```' },
  { id: 'tpl-debug', title: 'Debug an error', category: 'Code', content: 'Help me debug this. Explain the most likely cause, how to confirm it, and the fix:\n\n{{input}}' },
  { id: 'tpl-translate', title: 'Translate', category: 'Language', content: 'Translate the following into natural, fluent English (or into the language I name). Keep formatting:\n\n{{input}}' },
  { id: 'tpl-brainstorm', title: 'Brainstorm ideas', category: 'Thinking', content: 'Brainstorm 10 varied ideas for the following. Group them and mark the 3 most promising with a reason:\n\n{{input}}' },
  { id: 'tpl-pros-cons', title: 'Pros and cons', category: 'Thinking', content: 'Give a balanced pros and cons analysis, then a recommendation with the key assumption behind it:\n\n{{input}}' },
  { id: 'tpl-study', title: 'Quiz me', category: 'Learning', content: 'Create 5 quiz questions (mixed difficulty) about the following, then give the answers at the end:\n\n{{input}}' }
]);
