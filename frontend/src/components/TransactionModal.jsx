import React, { useState } from 'react';
import { X, Save, Loader2 } from 'lucide-react';

const IMPORTANCE_OPTIONS = [
  { value: 'ESSENTIAL', label: 'Essential', color: 'blue' },
  { value: 'HAVE_TO_HAVE', label: 'Have to Have', color: 'green' },
  { value: 'NICE_TO_HAVE', label: 'Nice to Have', color: 'orange' },
  { value: 'SHOULDNT_HAVE', label: "Shouldn't Have", color: 'red' },
];

const CYCLE_OPTIONS = [
  { value: 'ONE_TIME', label: 'One Time' },
  { value: 'WEEKLY', label: 'Weekly' },
  { value: 'MONTHLY', label: 'Monthly' },
  { value: 'YEARLY', label: 'Yearly' },
  { value: 'IRREGULAR', label: 'Irregular' },
];

const TYPE_OPTIONS = [
  { value: 'INCOME', label: 'Income' },
  { value: 'EXPENSE', label: 'Expense' },
];

export default function TransactionModal({ isOpen, onClose, onSave, categories, loading = false }) {
  const [formData, setFormData] = useState({
    title: '',
    amount: '',
    transactionDate: new Date().toISOString().split('T')[0],
    categoryId: '',
    notes: '',
    importance: 'ESSENTIAL',
    cycle: 'ONE_TIME',
    type: 'EXPENSE',
  });

  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  const handleChange = (field, value) => {
    setFormData(prev => ({ ...prev, [field]: value }));
    // Clear error when user starts typing
    if (errors[field]) {
      setErrors(prev => ({ ...prev, [field]: '' }));
    }
  };

  const validateForm = () => {
    const newErrors = {};

    if (!formData.title.trim()) {
      newErrors.title = 'Title is required';
    }

    if (!formData.amount || parseFloat(formData.amount) <= 0) {
      newErrors.amount = 'Amount must be greater than 0';
    }

    if (!formData.categoryId) {
      newErrors.categoryId = 'Category is required';
    }

    if (!formData.transactionDate) {
      newErrors.transactionDate = 'Date is required';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    
    if (!validateForm()) {
      return;
    }

    try {
      setSubmitting(true);
      await onSave({
        ...formData,
        amount: parseFloat(formData.amount),
        categoryId: parseInt(formData.categoryId),
      });
      
      // Reset form
      setFormData({
        title: '',
        amount: '',
        transactionDate: new Date().toISOString().split('T')[0],
        categoryId: '',
        notes: '',
        importance: 'ESSENTIAL',
        cycle: 'ONE_TIME',
        type: 'EXPENSE',
      });
      setErrors({});
      onClose();
    } catch (error) {
      console.error('Failed to save transaction:', error);
    } finally {
      setSubmitting(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
      <div className="bg-gray-900 border border-gray-800 rounded-xl p-6 w-full max-w-md max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between mb-6">
          <h2 className="text-xl font-semibold">Add New Transaction</h2>
          <button
            onClick={onClose}
            className="p-2 hover:bg-gray-800 rounded-lg transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          {/* Title */}
          <div>
            <label className="block text-sm font-medium text-gray-300 mb-2">
              Title *
            </label>
            <input
              type="text"
              value={formData.title}
              onChange={(e) => handleChange('title', e.target.value)}
              className={`w-full px-3 py-2 bg-gray-800/50 border rounded-lg focus:outline-none focus:border-violet-500 transition-colors ${
                errors.title ? 'border-red-500' : 'border-gray-700'
              }`}
              placeholder="Enter transaction title"
            />
            {errors.title && (
              <p className="text-red-400 text-xs mt-1">{errors.title}</p>
            )}
          </div>

          {/* Amount */}
          <div>
            <label className="block text-sm font-medium text-gray-300 mb-2">
              Amount *
            </label>
            <input
              type="number"
              step="0.01"
              min="0"
              value={formData.amount}
              onChange={(e) => handleChange('amount', e.target.value)}
              className={`w-full px-3 py-2 bg-gray-800/50 border rounded-lg focus:outline-none focus:border-violet-500 transition-colors ${
                errors.amount ? 'border-red-500' : 'border-gray-700'
              }`}
              placeholder="0.00"
            />
            {errors.amount && (
              <p className="text-red-400 text-xs mt-1">{errors.amount}</p>
            )}
          </div>

          {/* Date */}
          <div>
            <label className="block text-sm font-medium text-gray-300 mb-2">
              Date *
            </label>
            <input
              type="date"
              value={formData.transactionDate}
              onChange={(e) => handleChange('transactionDate', e.target.value)}
              className={`w-full px-3 py-2 bg-gray-800/50 border rounded-lg focus:outline-none focus:border-violet-500 transition-colors ${
                errors.transactionDate ? 'border-red-500' : 'border-gray-700'
              }`}
            />
            {errors.transactionDate && (
              <p className="text-red-400 text-xs mt-1">{errors.transactionDate}</p>
            )}
          </div>

          {/* Type */}
          <div>
            <label className="block text-sm font-medium text-gray-300 mb-2">
              Type *
            </label>
            <select
              value={formData.type}
              onChange={(e) => handleChange('type', e.target.value)}
              className="w-full px-3 py-2 bg-gray-800/50 border border-gray-700 rounded-lg focus:outline-none focus:border-violet-500 transition-colors"
            >
              {TYPE_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>

          {/* Category */}
          <div>
            <label className="block text-sm font-medium text-gray-300 mb-2">
              Category *
            </label>
            <select
              value={formData.categoryId}
              onChange={(e) => handleChange('categoryId', e.target.value)}
              className={`w-full px-3 py-2 bg-gray-800/50 border rounded-lg focus:outline-none focus:border-violet-500 transition-colors ${
                errors.categoryId ? 'border-red-500' : 'border-gray-700'
              }`}
            >
              <option value="">Select a category</option>
              {categories.map((category) => (
                <option key={category.id} value={category.id}>
                  {category.name}
                </option>
              ))}
            </select>
            {errors.categoryId && (
              <p className="text-red-400 text-xs mt-1">{errors.categoryId}</p>
            )}
          </div>

          {/* Importance */}
          <div>
            <label className="block text-sm font-medium text-gray-300 mb-2">
              Importance *
            </label>
            <div className="grid grid-cols-2 gap-2">
              {IMPORTANCE_OPTIONS.map((option) => {
                const isSelected = formData.importance === option.value;
                let selectedClasses = '';
                
                switch (option.color) {
                  case 'blue':
                    selectedClasses = 'bg-green-700/20 text-green-500 border-green-500/30';
                    break;
                  case 'green':
                    selectedClasses = 'bg-yellow-700/20 text-yellow-500 border-yellow-500/30';
                    break;
                  case 'orange':
                    selectedClasses = 'bg-orange-700/20 text-orange-500 border-orange-500/30';
                    break;
                  case 'red':
                    selectedClasses = 'bg-red-700/20 text-red-500 border-red-500/30';
                    break;
                  default:
                    selectedClasses = 'bg-gray-500/20 text-gray-400 border-gray-500/30';
                }
                
                return (
                  <button
                    key={option.value}
                    type="button"
                    onClick={() => handleChange('importance', option.value)}
                    className={`px-3 py-2 rounded-lg border text-sm font-medium transition-colors ${
                      isSelected
                        ? selectedClasses
                        : 'bg-gray-800/50 text-gray-400 border-gray-700 hover:border-gray-600'
                    }`}
                  >
                    {option.label}
                  </button>
                );
              })}
            </div>
          </div>

          {/* Cycle */}
          <div>
            <label className="block text-sm font-medium text-gray-300 mb-2">
              Cycle *
            </label>
            <select
              value={formData.cycle}
              onChange={(e) => handleChange('cycle', e.target.value)}
              className="w-full px-3 py-2 bg-gray-800/50 border border-gray-700 rounded-lg focus:outline-none focus:border-violet-500 transition-colors"
            >
              {CYCLE_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>

          {/* Notes */}
          <div>
            <label className="block text-sm font-medium text-gray-300 mb-2">
              Notes
            </label>
            <textarea
              value={formData.notes}
              onChange={(e) => handleChange('notes', e.target.value)}
              rows={3}
              className="w-full px-3 py-2 bg-gray-800/50 border border-gray-700 rounded-lg focus:outline-none focus:border-violet-500 transition-colors resize-none"
              placeholder="Add any additional notes..."
            />
          </div>

          {/* Submit Button */}
          <div className="flex gap-3 pt-4">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 px-4 py-2 bg-gray-800 hover:bg-gray-700 text-gray-300 rounded-lg transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="flex-1 px-4 py-2 bg-gradient-to-r from-violet-500 to-purple-600 hover:from-violet-600 hover:to-purple-700 text-white rounded-lg transition-all disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
            >
              {submitting ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  Saving...
                </>
              ) : (
                <>
                  <Save className="w-4 h-4" />
                  Save Transaction
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
